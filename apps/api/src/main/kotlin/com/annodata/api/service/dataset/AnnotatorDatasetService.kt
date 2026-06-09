package com.annodata.api.service.dataset

import com.annodata.api.db.DatasetsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.AnnotationsTable
import com.annodata.api.db.AnnotationTaskBatchesTable
import com.annodata.api.db.AnnotationTasksTable
import com.annodata.api.models.ClaimTasksResponse
import com.annodata.api.models.DataItemResponse
import com.annodata.api.models.DatasetResponse
import com.annodata.api.models.AnnotatorTaskWorkspaceResponse
import com.annodata.api.models.AnnotationSubmissionInput
import com.annodata.api.models.AnnotatorTaskDetailResponse
import com.annodata.api.models.AnnotatorTaskResponse
import com.annodata.api.models.SubmitAnnotationBatchResponse
import com.annodata.api.models.TaskAssignmentResponse
import com.annodata.api.models.UpdateDatasetResponse
import com.annodata.api.http.Result
import com.annodata.api.service.dataset.model.BatchTaskRows
import com.annodata.api.service.dataset.model.ParsedSubmission
import com.annodata.api.service.dataset.policy.AnnotationReviewPolicy
import com.annodata.api.service.dataset.policy.ClaimPolicy
import com.annodata.api.service.dataset.policy.DatasetStatusPolicy
import com.annodata.api.service.dataset.result.ClaimTasksTransactionResult
import com.annodata.api.service.dataset.result.ReturnTaskTransactionResult
import com.annodata.api.service.dataset.result.StartTaskBatchTransactionResult
import com.annodata.api.service.dataset.result.SubmitAnnotationBatchResult
import com.annodata.api.service.dataset.store.AnnotationStore
import com.annodata.api.service.dataset.store.AnnotationTaskStore
import com.annodata.api.service.dataset.store.DataItemStore
import com.annodata.api.service.dataset.store.DatasetStore
import com.annodata.api.service.dataset.store.ClaimedDataItem
import com.annodata.api.service.dataset.view.toDatasetResponse
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 数据标注员业务服务，封装标注员侧数据集领取、任务和提交逻辑。
 */
class AnnotatorDatasetService {
    private val datasetStore = DatasetStore()
    private val dataItemStore = DataItemStore()
    private val annotationTaskStore = AnnotationTaskStore()
    private val annotationStore = AnnotationStore()
    private val activeTaskStatuses = listOf("assigned", "in_progress")
    private val activeBatchStatuses = listOf("assigned", "in_progress")

    /**
     * 查询标注员可领取任务的开放数据集列表。
     *
     * 返回每个数据集的标注余量（pending 且当前标注员未参与过的数据项数量）和互查余量
     * （annotated/disputed 且当前标注员未参与过的数据项数量），用于前端展示和领取数量限制。
     *
     * @param annotatorId 标注员用户 ID
     * @return 查询结果，成功时返回数据集列表
     */
    fun listOpenDatasets(annotatorId: UUID): Result<List<DatasetResponse>> {
        val datasets = transaction {
            // 查询所有仍可继续处理的数据集，作为标注员可领取任务的候选列表。
            val datasetRows = datasetStore.listClaimableDatasetRows()

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }

            // 查询每个可处理数据集下当前标注员仍可领取的 pending 数据项数量。
            val pendingCounts = dataItemStore.countClaimablePendingItemsByDataset(annotatorId, datasetIds)

            val activeByDataset = annotationTaskStore.countActiveBatchesByDataset(annotatorId, datasetIds, activeBatchStatuses)

            // 查询当前标注员在每个数据集下是否已有互查类型的活跃任务单。
            val activeReviewByDataset = annotationTaskStore.countActiveBatchesByDataset(
                annotatorId = annotatorId,
                datasetIds = datasetIds,
                activeBatchStatuses = activeBatchStatuses,
                batchType = "review",
            )

            // 限制同时持有的活跃任务项总数不超过 5 个，避免单用户过度占用系统资源。
            val totalActive = annotationTaskStore.countActiveBatchItems(annotatorId, activeBatchStatuses)

            // 查询每个数据集下“当前轮可互查”的数据项数量。
            // 这里的可互查不是看全历史，而是只看当前轮：
            // 1. 当前轮已有原始标注；
            // 2. 当前轮还没有 review 标注；
            // 3. 当前标注员当前轮未参与过该数据项。
            val reviewableCounts = dataItemStore.countReviewableItemsByDataset(annotatorId, datasetIds)

            // 查询每个数据集中已经形成最终结果的数据项数量。
            val completedCounts = DatasetQueryHelper.countCompletedItems(datasetIds)

            datasetRows.map { row ->
                val datasetId = row[DatasetsTable.id]
                val hasPending = (pendingCounts[datasetId] ?: 0) > 0
                // 前端看到的“互查 N 条”就是这里的 reviewableCounts。
                // 若为 0，表示当前轮不存在可领取的互查任务，不代表这个数据集历史上没有标注记录。
                val hasReviewable = (reviewableCounts[datasetId] ?: 0) > 0
                val hasActiveInDataset = (activeByDataset[datasetId] ?: 0) > 0
                val hasActiveReviewInDataset = (activeReviewByDataset[datasetId] ?: 0) > 0
                val canClaimAnnotation = ClaimPolicy.canClaimAnnotation(hasPending, hasActiveInDataset, totalActive)
                val canClaimReview = ClaimPolicy.canClaimReview(hasReviewable, hasActiveReviewInDataset, totalActive)
                val canClaim = canClaimAnnotation || canClaimReview

                val pendingCount = (pendingCounts[datasetId] ?: 0)
                val reviewableCount = reviewableCounts[datasetId] ?: 0
                val completedCount = completedCounts[datasetId] ?: 0

                toDatasetResponse(
                    row,
                    canClaim = canClaim,
                    pendingItemCount = pendingCount,
                    reviewableItemCount = reviewableCount,
                    completedItemCount = completedCount,
                )
            }
        }

        return Result.Success(datasets)
    }

    /**
     * 为标注员领取指定数据集的任务。
     *
     * @param annotatorId 标注员用户 ID
     * @param datasetId 数据集 ID
     * @param requestedCount 期望领取的任务数量
     * @param taskType 任务类别：`annotation` 标注任务，`review` 互查任务
     * @return 领取结果
     */
    fun claimAnnotatorTasks(
        annotatorId: UUID,
        datasetId: UUID,
        requestedCount: Int,
        taskType: String,
    ): Result<ClaimTasksResponse> {
        if (requestedCount <= 0) {
            return Result.BadRequest("领取数量必须大于 0")
        }
        if (taskType !in listOf("annotation", "review")) {
            return Result.BadRequest("任务类别必须是 annotation 或 review")
        }

        val result = transaction {
            // 查询目标数据集，确认数据集存在且可被领取。
            val dataset = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction ClaimTasksTransactionResult.NotFound

            if (!DatasetStatusPolicy.canClaimDataset(dataset[DatasetsTable.status])) {
                return@transaction ClaimTasksTransactionResult.InvalidStatus
            }

            // 查询当前标注员是否已持有该数据集同类型的活跃任务单。
            val existingInDataset = annotationTaskStore.countExistingActiveBatchesInDataset(
                annotatorId = annotatorId,
                datasetId = datasetId,
                taskType = taskType,
                activeBatchStatuses = activeBatchStatuses,
            )

            if (existingInDataset > 0) {
                return@transaction ClaimTasksTransactionResult.AlreadyClaimed
            }

            // 查询当前标注员所有活跃任务单的任务项总数，控制并发持有上限。
            val totalActive = annotationTaskStore.countActiveBatchItems(annotatorId, activeBatchStatuses)

            // 限制同时持有的活跃任务项总数不超过 5 个，避免单用户过度占用系统资源。
            if (ClaimPolicy.hasReachedActiveTaskLimit(totalActive)) {
                return@transaction ClaimTasksTransactionResult.TooManyActive
            }

            val now = OffsetDateTime.now()

            if (taskType == "annotation") {
                val claimedItems = dataItemStore.claimPendingItemsForAnnotation(datasetId, annotatorId, requestedCount, now)
                if (claimedItems.isEmpty()) {
                    return@transaction ClaimTasksTransactionResult.EmptyDataset
                }

                return@transaction createAnnotationClaimResult(
                    datasetId = datasetId,
                    annotatorId = annotatorId,
                    taskType = taskType,
                    claimedItems = claimedItems,
                    now = now,
                )
            }

            val items = run {
                // 互查任务：只查询当前轮已有原始标注、但当前轮尚未互查的数据项，
                // 同时排除当前标注员当前轮已经参与过的数据项。
                dataItemStore.findReviewableItemRows(listOf(datasetId), annotatorId, requestedCount)
            }

            if (items.isEmpty()) {
                return@transaction ClaimTasksTransactionResult.EmptyDataset
            }

            val batchId = UUID.randomUUID()
            val orderNo = generateOrderNo(now, taskType)

            // 创建任务单记录。
            annotationTaskStore.createTaskBatch(batchId, orderNo, datasetId, annotatorId, taskType, items.size, now)

            val tasks = items.map { item ->
                val taskId = UUID.randomUUID()

                annotationTaskStore.createAnnotationTask(
                    taskId = taskId,
                    batchId = batchId,
                    datasetId = datasetId,
                    itemId = item[DataItemsTable.id],
                    annotatorId = annotatorId,
                    roundNo = item[DataItemsTable.currentRoundNo],
                    now = now,
                )

                TaskAssignmentResponse(
                    taskId = taskId.toString(),
                    item = DataItemResponse(
                        id = item[DataItemsTable.id].toString(),
                        datasetId = item[DataItemsTable.datasetId].toString(),
                        content = item[DataItemsTable.content],
                        contentType = item[DataItemsTable.contentType],
                        metadata = item[DataItemsTable.metadata],
                        currentRoundNo = item[DataItemsTable.currentRoundNo],
                        finalResult = item[DataItemsTable.finalResult],
                        finalizedAt = item[DataItemsTable.finalizedAt]?.toString(),
                        finalizedBy = item[DataItemsTable.finalizedBy]?.toString(),
                        status = item[DataItemsTable.status],
                        createdAt = item[DataItemsTable.createdAt].toString(),
                        updatedAt = now.toString(),
                    )
                )
            }

            ClaimTasksTransactionResult.Success(
                ClaimTasksResponse(
                    batchId = batchId.toString(),
                    orderNo = orderNo,
                    datasetId = datasetId.toString(),
                    assignedCount = tasks.size,
                    tasks = tasks,
                )
            )
        }

        return when (result) {
            ClaimTasksTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            ClaimTasksTransactionResult.InvalidStatus -> Result.BadRequest("该数据集当前不可领取")
            ClaimTasksTransactionResult.AlreadyClaimed -> Result.BadRequest("该数据集已有未完成任务单")
            ClaimTasksTransactionResult.TooManyActive -> Result.BadRequest("当前进行中的任务已达上限")
            ClaimTasksTransactionResult.EmptyDataset -> Result.BadRequest("该数据集暂无可领取任务")
            is ClaimTasksTransactionResult.Success -> Result.Success(result.value)
        }
    }

    private fun createAnnotationClaimResult(
        datasetId: UUID,
        annotatorId: UUID,
        taskType: String,
        claimedItems: List<ClaimedDataItem>,
        now: OffsetDateTime,
    ): ClaimTasksTransactionResult.Success {
        val batchId = UUID.randomUUID()
        val orderNo = generateOrderNo(now, taskType)

        annotationTaskStore.createTaskBatch(batchId, orderNo, datasetId, annotatorId, taskType, claimedItems.size, now)

        val tasks = claimedItems.map { item ->
            val taskId = UUID.randomUUID()

            annotationTaskStore.createAnnotationTask(
                taskId = taskId,
                batchId = batchId,
                datasetId = datasetId,
                itemId = item.id,
                annotatorId = annotatorId,
                roundNo = item.currentRoundNo,
                now = now,
            )

            TaskAssignmentResponse(
                taskId = taskId.toString(),
                item = DataItemResponse(
                    id = item.id.toString(),
                    datasetId = item.datasetId.toString(),
                    content = item.content,
                    contentType = item.contentType,
                    metadata = item.metadata,
                    currentRoundNo = item.currentRoundNo,
                    finalResult = item.finalResult,
                    finalizedAt = item.finalizedAt?.toString(),
                    finalizedBy = item.finalizedBy?.toString(),
                    status = item.status,
                    createdAt = item.createdAt.toString(),
                    updatedAt = item.updatedAt.toString(),
                )
            )
        }

        return ClaimTasksTransactionResult.Success(
            ClaimTasksResponse(
                batchId = batchId.toString(),
                orderNo = orderNo,
                datasetId = datasetId.toString(),
                assignedCount = tasks.size,
                tasks = tasks,
            )
        )
    }

    /**
     * 生成任务单编号。
     *
     * @param now 当前时间，用于生成时间戳前缀
     * @param taskType 任务类型，`review` 时前缀为 REVIEW，否则为 TASK
     * @return 格式为 `{PREFIX}-{yyyyMMddHHmmss}-{6位随机后缀}` 的订单号
     */
    private fun generateOrderNo(now: OffsetDateTime, taskType: String = "annotation"): String {
        val timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val suffix = UUID.randomUUID().toString().take(6).uppercase()
        val prefix = if (taskType == "review") "REVIEW" else "TASK"
        return "$prefix-${timestamp}-${suffix}"
    }

    /**
     * 批量查询任务单详情所需的任务项、数据项和已有标注结果。
     *
     * @param annotatorId 标注员用户 ID，用于校验任务单归属
     * @param batchId 任务单 ID
     * @return 包含任务项列表、标注结果映射和数据项映射的复合查询结果
     */
    private fun fetchBatchTaskRows(
        annotatorId: UUID,
        batchId: UUID,
    ): BatchTaskRows {
        val taskRows = annotationTaskStore.listTaskRowsForBatch(annotatorId, batchId)

        val taskIds = taskRows.map { it[AnnotationTasksTable.id] }
        val itemIds = taskRows.map { it[AnnotationTasksTable.itemId] }.toSet()

        val annotationsByTask = annotationStore.listAnnotationsByTaskIds(taskIds)
        val items = dataItemStore.listItemsByIds(itemIds)

        return BatchTaskRows(
            taskRows = taskRows,
            annotationsByTask = annotationsByTask,
            items = items,
        )
    }

    /**
     * 将批量查询的原始数据转换为任务详情响应列表。
     *
     * @param batchId 任务单 ID
     * @param orderNo 任务单编号
     * @param batchTasks 包含任务项、标注结果和数据项的复合查询结果
     * @return 任务详情响应列表，数据项缺失时自动过滤
     */
    private fun toTaskDetailResponses(
        batchId: UUID,
        orderNo: String,
        batchTasks: BatchTaskRows,
    ): List<AnnotatorTaskDetailResponse> {
        return batchTasks.taskRows.mapNotNull { taskRow ->
            val itemId = taskRow[AnnotationTasksTable.itemId]
            val item = batchTasks.items[itemId] ?: return@mapNotNull null
            val annotation = batchTasks.annotationsByTask[taskRow[AnnotationTasksTable.id]]

            AnnotatorTaskDetailResponse(
                batchId = batchId.toString(),
                orderNo = orderNo,
                taskId = taskRow[AnnotationTasksTable.id].toString(),
                item = DataItemResponse(
                    id = item[DataItemsTable.id].toString(),
                    datasetId = item[DataItemsTable.datasetId].toString(),
                    content = item[DataItemsTable.content],
                    contentType = item[DataItemsTable.contentType],
                    metadata = item[DataItemsTable.metadata],
                    finalResult = item[DataItemsTable.finalResult],
                    finalizedAt = item[DataItemsTable.finalizedAt]?.toString(),
                    finalizedBy = item[DataItemsTable.finalizedBy]?.toString(),
                    status = item[DataItemsTable.status],
                    createdAt = item[DataItemsTable.createdAt].toString(),
                    updatedAt = item[DataItemsTable.updatedAt].toString(),
                ),
                status = taskRow[AnnotationTasksTable.status],
                assignedAt = taskRow[AnnotationTasksTable.assignedAt].toString(),
                startedAt = taskRow[AnnotationTasksTable.startedAt]?.toString(),
                submittedAt = taskRow[AnnotationTasksTable.submittedAt]?.toString(),
                annotationResult = annotation?.get(AnnotationsTable.result),
                annotationIsDisputed = annotation?.get(AnnotationsTable.isDisputed),
                annotationStatus = annotation?.get(AnnotationsTable.status),
                adoptionStatus = annotation?.get(AnnotationsTable.adoptionStatus)?.toInt(),
                adoptionComment = annotation?.get(AnnotationsTable.adoptionComment),
            )
        }
    }

    /**
     * 查询指定标注员已领取的任务单列表。
     *
     * @param annotatorId 标注员用户 ID
     * @param statusFilters 任务单状态筛选列表，null 或空时不筛选
     * @return 查询结果，成功时返回任务单列表
     */
    fun listAnnotatorTasks(
        annotatorId: UUID,
        statusFilters: List<String>?,
    ): Result<List<AnnotatorTaskResponse>> {
        val taskBatches = transaction {
            // 查询当前标注员的任务单列表，可按任务单状态筛选。
            val batchRows = AnnotationTaskBatchesTable
                .select(
                    AnnotationTaskBatchesTable.id,
                    AnnotationTaskBatchesTable.orderNo,
                    AnnotationTaskBatchesTable.datasetId,
                    AnnotationTaskBatchesTable.status,
                    AnnotationTaskBatchesTable.totalCount,
                    AnnotationTaskBatchesTable.assignedAt,
                    AnnotationTaskBatchesTable.startedAt,
                    AnnotationTaskBatchesTable.submittedAt,
                )
                .where {
                    val baseCondition = AnnotationTaskBatchesTable.annotatorId eq annotatorId
                    if (!statusFilters.isNullOrEmpty()) {
                        baseCondition and (AnnotationTaskBatchesTable.status inList statusFilters)
                    } else {
                        baseCondition
                    }
                }
                .orderBy(AnnotationTaskBatchesTable.assignedAt to SortOrder.DESC)
                .toList()

            val datasetIds = batchRows.map { it[AnnotationTaskBatchesTable.datasetId] }.toSet()
            val batchIds = batchRows.map { it[AnnotationTaskBatchesTable.id] }.toSet()

            val datasets = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务单关联的数据集名称，避免逐条查数据集。
                DatasetsTable
                    .select(DatasetsTable.id, DatasetsTable.name)
                    .where { DatasetsTable.id inList datasetIds }
                    .associateBy { it[DatasetsTable.id] }
            }

            // 批量统计任务单下各状态任务项数量，避免拉取所有任务项行后本地分组。
            val taskCountsByBatch = annotationTaskStore.countTaskStatusesByBatch(batchIds)

            batchRows.mapNotNull { batch ->
                val datasetId = batch[AnnotationTaskBatchesTable.datasetId]
                val dataset = datasets[datasetId] ?: return@mapNotNull null
                val taskCounts = taskCountsByBatch[batch[AnnotationTaskBatchesTable.id]]

                AnnotatorTaskResponse(
                    batchId = batch[AnnotationTaskBatchesTable.id].toString(),
                    orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                    datasetId = datasetId.toString(),
                    datasetName = dataset[DatasetsTable.name],
                    status = batch[AnnotationTaskBatchesTable.status],
                    totalCount = batch[AnnotationTaskBatchesTable.totalCount],
                    assignedCount = taskCounts?.assigned ?: 0,
                    inProgressCount = taskCounts?.inProgress ?: 0,
                    submittedCount = taskCounts?.submitted ?: 0,
                    assignedAt = batch[AnnotationTaskBatchesTable.assignedAt].toString(),
                    startedAt = batch[AnnotationTaskBatchesTable.startedAt]?.toString(),
                    submittedAt = batch[AnnotationTaskBatchesTable.submittedAt]?.toString(),
                )
            }
        }

        return Result.Success(taskBatches)
    }

    /**
     * 查询标注员在指定任务单下的任务详情列表。
     *
     * @param annotatorId 标注员用户 ID
     * @param batchId 任务单 ID
     * @return 查询结果，成功时返回任务详情列表
     */
    fun listBatchTasks(
        annotatorId: UUID,
        batchId: UUID,
    ): Result<List<AnnotatorTaskDetailResponse>> {
        val result = transaction {
            // 查询任务单并校验其属于当前标注员。
            val batch = annotationTaskStore.findBatchRowForAnnotator(batchId, annotatorId)
                ?: return@transaction null

            toTaskDetailResponses(
                batchId = batchId,
                orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                batchTasks = fetchBatchTaskRows(annotatorId, batchId),
            )
        }

        return if (result == null) {
            Result.BadRequest("任务单不存在或无权访问")
        } else {
            Result.Success(result)
        }
    }

    /**
     * 标注员退回指定任务单下的所有活跃任务项。
     *
     * @param annotatorId 标注员用户 ID
     * @param batchId 任务单 ID
     * @return 退回结果
     */
    fun returnTaskBatch(
        annotatorId: UUID,
        batchId: UUID,
    ): Result<UpdateDatasetResponse> {
        val result = transaction {
            // 查询任务单并校验其属于当前标注员。
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction ReturnTaskTransactionResult.NotFound

            if (batch[AnnotationTaskBatchesTable.status] !in activeBatchStatuses) {
                return@transaction ReturnTaskTransactionResult.InvalidStatus
            }

            // 查询任务单下仍可退回的活跃任务项。
            val tasks = AnnotationTasksTable
                .selectAll()
                .where {
                    (AnnotationTasksTable.annotatorId eq annotatorId) and
                        (AnnotationTasksTable.batchId eq batchId) and
                        (AnnotationTasksTable.status inList activeTaskStatuses)
                }
                .toList()

            if (tasks.isEmpty()) {
                return@transaction ReturnTaskTransactionResult.NotFound
            }

            val now = OffsetDateTime.now()
            val isReviewBatch = batch[AnnotationTaskBatchesTable.batchType] == "review"

            tasks.forEach { task ->
                val taskId = task[AnnotationTasksTable.id]
                val itemId = task[AnnotationTasksTable.itemId]

                AnnotationTasksTable.update({ AnnotationTasksTable.id eq taskId }) {
                    it[status] = "cancelled"
                    it[updatedAt] = now
                }

                // 仅标注任务退回时才恢复数据项状态为 pending；互查任务不影响原标注状态。
                // 互查退回后原始标注仍然有效，数据项保持 annotated/assigned 等原有状态。
                if (!isReviewBatch) {
                    DataItemsTable.update({ DataItemsTable.id eq itemId }) {
                        it[status] = "pending"
                        it[updatedAt] = now
                    }
                }
            }

            AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
                it[status] = "cancelled"
                it[updatedAt] = now
            }

            ReturnTaskTransactionResult.Success
        }

        return when (result) {
            ReturnTaskTransactionResult.NotFound -> Result.BadRequest("该数据集下没有可退回的任务")
            ReturnTaskTransactionResult.InvalidStatus -> Result.BadRequest("仅可退回已分配或进行中的任务")
            ReturnTaskTransactionResult.Success -> Result.Success(UpdateDatasetResponse("任务已退回"))
        }
    }

    /**
     * 标注员开始指定任务单。
     *
     * @param annotatorId 标注员用户 ID
     * @param batchId 任务单 ID
     * @return 开始结果
     */
    fun startTaskBatch(
        annotatorId: UUID,
        batchId: UUID,
    ): Result<UpdateDatasetResponse> {
        val result = transaction {
            // 查询任务单并校验其属于当前标注员。
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction StartTaskBatchTransactionResult.NotFound

            if (batch[AnnotationTaskBatchesTable.status] !in activeBatchStatuses) {
                return@transaction StartTaskBatchTransactionResult.InvalidStatus
            }

            val now = OffsetDateTime.now()
            AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
                it[status] = "in_progress"
                if (batch[AnnotationTaskBatchesTable.startedAt] == null) {
                    it[startedAt] = now
                }
                it[updatedAt] = now
            }

            AnnotationTasksTable.update({
                (AnnotationTasksTable.batchId eq batchId) and
                    (AnnotationTasksTable.annotatorId eq annotatorId) and
                    (AnnotationTasksTable.status eq "assigned")
            }) {
                it[status] = "in_progress"
                it[startedAt] = now
                it[updatedAt] = now
            }

            StartTaskBatchTransactionResult.Success
        }

        return when (result) {
            StartTaskBatchTransactionResult.NotFound -> Result.BadRequest("任务单不存在或无权访问")
            StartTaskBatchTransactionResult.InvalidStatus -> Result.BadRequest("仅可开始已领取或进行中的任务单")
            StartTaskBatchTransactionResult.Success -> Result.Success(UpdateDatasetResponse("任务单已开始"))
        }
    }

    /**
     * 查询标注员在指定任务单下的工作台数据。
     *
     * @param annotatorId 标注员用户 ID
     * @param batchId 任务单 ID
     * @return 查询结果，成功时返回工作台数据
     */
    fun getAnnotatorTaskWorkspace(
        annotatorId: UUID,
        batchId: UUID,
    ): Result<AnnotatorTaskWorkspaceResponse> {
        val result = transaction {
            // 查询任务单并校验其属于当前标注员。
            val batch = annotationTaskStore.findBatchRowForAnnotator(batchId, annotatorId)
                ?: return@transaction null

            // 查询任务单所属的数据集，获取标注说明和标注配置。
            val dataset = DatasetsTable
                .selectAll()
                .where { DatasetsTable.id eq batch[AnnotationTaskBatchesTable.datasetId] }
                .limit(1)
                .firstOrNull()
                ?: return@transaction null

            val tasks = toTaskDetailResponses(
                batchId = batchId,
                orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                batchTasks = fetchBatchTaskRows(annotatorId, batchId),
            )

            AnnotatorTaskWorkspaceResponse(
                batchId = batchId.toString(),
                orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                datasetId = dataset[DatasetsTable.id].toString(),
                datasetName = dataset[DatasetsTable.name],
                annotationGuide = dataset[DatasetsTable.annotationGuide],
                annotationSchema = dataset[DatasetsTable.annotationSchema],
                totalCount = batch[AnnotationTaskBatchesTable.totalCount],
                submittedCount = tasks.count { it.status == "submitted" },
                tasks = tasks,
            )
        }

        return if (result == null) {
            Result.BadRequest("任务单不存在或无权访问")
        } else {
            Result.Success(result)
        }
    }

    /**
     * 批量提交标注员在任务单下的标注结果。
     *
     * 标注任务提交时仅写入原始标注结果。
     * 互查任务（`batch_type = 'review'`）提交时会新增或更新 review 类型标注记录，
     * 并通过 `annotations.review_of_annotation_id` 指向原始标注结果。原始标注与互查均完成后，
     * 系统才会推进 `data_items.status` 为 `annotated` 或 `disputed`。
     *
     * @param annotatorId 标注员用户 ID
     * @param batchId 任务单 ID
     * @param submissions 标注结果输入
     * @return 提交结果
     */
    fun submitAnnotationBatch(
        annotatorId: UUID,
        batchId: UUID,
        submissions: List<AnnotationSubmissionInput>,
    ): Result<SubmitAnnotationBatchResponse> {
        if (submissions.isEmpty()) {
            return Result.BadRequest("提交内容不能为空")
        }

        val parsedSubmissions = submissions.mapNotNull { submission ->
            runCatching {
                ParsedSubmission(
                    taskId = UUID.fromString(submission.taskId),
                    itemId = UUID.fromString(submission.itemId),
                    result = submission.result.ifEmpty { "{}" },
                    isDisputed = submission.isDisputed,
                    comment = submission.comment?.trim()?.takeIf { it.isNotEmpty() },
                )
            }.getOrNull()
        }

        if (parsedSubmissions.size != submissions.size) {
            return Result.BadRequest("提交内容格式不正确")
        }

        val result = transaction {
            // 查询任务单并校验其属于当前标注员。
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction SubmitAnnotationBatchResult.NotFound

            if (batch[AnnotationTaskBatchesTable.status] !in activeBatchStatuses) {
                return@transaction SubmitAnnotationBatchResult.InvalidStatus
            }

            val taskIds = parsedSubmissions.map { it.taskId }

            // 查询本次提交涉及的任务项，确保都属于当前任务单和标注员。
            val taskRows = AnnotationTasksTable
                .selectAll()
                .where {
                    (AnnotationTasksTable.annotatorId eq annotatorId) and
                        (AnnotationTasksTable.batchId eq batchId) and
                        (AnnotationTasksTable.id inList taskIds)
                }
                .toList()

            if (taskRows.size != parsedSubmissions.size) {
                return@transaction SubmitAnnotationBatchResult.InvalidTasks
            }

            val tasksById = taskRows.associateBy { it[AnnotationTasksTable.id] }
            val now = OffsetDateTime.now()

            parsedSubmissions.forEach { submission ->
                val taskRow = tasksById[submission.taskId] ?: return@forEach
                val itemId = taskRow[AnnotationTasksTable.itemId]

                if (itemId != submission.itemId) {
                    return@transaction SubmitAnnotationBatchResult.InvalidTasks
                }

                // 查询任务项是否已有标注结果，决定后续插入还是覆盖更新。
                val existingAnnotation = annotationStore.findExistingAnnotationByTaskId(submission.taskId)

                val isReviewBatch = batch[AnnotationTaskBatchesTable.batchType] == "review"
                val originalAnnotation = if (isReviewBatch) {
                    annotationStore.findOriginalAnnotation(submission.itemId, taskRow[AnnotationTasksTable.roundNo])
                        ?: return@transaction SubmitAnnotationBatchResult.InvalidTasks
                } else null
                // 互查任务需要比对原始标注与互查结果的一致性：
                // - 若原始标注已被标记争议，或互查员主动标记争议，或结果不一致，则视为争议。
                // 标注任务直接使用提交时的争议标记。
                val isDisputed = AnnotationReviewPolicy.resolveSubmittedAnnotationDispute(
                    isReviewBatch = isReviewBatch,
                    submissionMarkedDisputed = submission.isDisputed,
                    originalAnnotationMarkedDisputed = originalAnnotation?.get(AnnotationsTable.isDisputed) ?: false,
                    originalResult = originalAnnotation?.get(AnnotationsTable.result),
                    submittedResult = submission.result,
                )

                annotationStore.upsertSubmittedAnnotation(
                    existingAnnotation = existingAnnotation,
                    taskId = submission.taskId,
                    itemId = submission.itemId,
                    annotatorId = annotatorId,
                    result = submission.result,
                    annotationType = if (isReviewBatch) "review" else "annotation",
                    reviewOfAnnotationId = originalAnnotation?.get(AnnotationsTable.id),
                    roundNo = taskRow[AnnotationTasksTable.roundNo],
                    comment = submission.comment,
                    isDisputed = isDisputed,
                    reviewedAt = if (isReviewBatch) now else null,
                    now = now,
                )

                annotationTaskStore.markTaskSubmitted(submission.taskId, now)

                // 互查提交完成后，触发数据项状态机推进（一致则采纳，不一致则争议）。
                if (isReviewBatch && originalAnnotation != null) {
                    finalizeReviewedItem(
                        datasetId = taskRow[AnnotationTasksTable.datasetId],
                        itemId = submission.itemId,
                        originalAnnotationId = originalAnnotation[AnnotationsTable.id],
                        roundNo = taskRow[AnnotationTasksTable.roundNo],
                        now = now,
                    )
                }
            }

            // 查询任务单下已提交的任务项数量，用于判断整单是否完成。
            val submittedCount = annotationTaskStore.countSubmittedTasksInBatch(batchId)

            if (batch[AnnotationTaskBatchesTable.status] == "assigned") {
                annotationTaskStore.markBatchInProgress(batchId, batch[AnnotationTaskBatchesTable.startedAt] == null, now)
            }

            if (batch[AnnotationTaskBatchesTable.totalCount] in 1..submittedCount) {
                annotationTaskStore.markBatchSubmitted(batchId, now)
            }

            SubmitAnnotationBatchResult.Success(submittedCount, batch[AnnotationTaskBatchesTable.totalCount])
        }

        return when (result) {
            SubmitAnnotationBatchResult.NotFound -> Result.BadRequest("任务单不存在或无权访问")
            SubmitAnnotationBatchResult.InvalidStatus -> Result.BadRequest("仅可提交已领取或进行中的任务单")
            SubmitAnnotationBatchResult.InvalidTasks -> Result.BadRequest("任务内容不匹配或无权访问")
            is SubmitAnnotationBatchResult.Success -> Result.Success(
                SubmitAnnotationBatchResponse(
                    message = "提交成功",
                    submittedCount = result.submittedCount,
                    totalCount = result.totalCount,
                )
            )
        }
    }
    /**
     * 原始标注与互查均提交后，推进数据项的最终状态。
     *
     * 状态机规则：
     * - 若原始标注与互查结果一致且无争议标记 → 自动采纳，标注记录变为 accepted，数据项状态变为 annotated
     * - 若结果不一致或存在争议标记 → 标注记录保持 submitted，数据项状态变为 disputed，等待提供者裁决
     *
     * 同时更新两条标注记录的采纳状态和采纳时间，并刷新数据集完成计数。
     *
     * @param datasetId 数据项所属的数据集 ID，用于刷新完成计数
     * @param itemId 数据项 ID
     * @param originalAnnotationId 原始标注记录 ID，用于定位原始标注
     * @param now 当前时间，用于更新各字段的时间戳
     */
    private fun finalizeReviewedItem(
        datasetId: UUID,
        itemId: UUID,
        originalAnnotationId: UUID,
        roundNo: Int,
        now: OffsetDateTime,
    ) {
        val annotations = annotationStore.listRoundAnnotations(itemId, roundNo)

        val originalAnnotation = annotations.firstOrNull { it[AnnotationsTable.id] == originalAnnotationId } ?: return
        val reviewAnnotation = annotations.firstOrNull { it[AnnotationsTable.annotationType] == "review" } ?: return
        val isDisputed = AnnotationReviewPolicy.shouldFinalizeAsDisputed(
            originalMarkedDisputed = originalAnnotation[AnnotationsTable.isDisputed],
            reviewMarkedDisputed = reviewAnnotation[AnnotationsTable.isDisputed],
            originalResult = originalAnnotation[AnnotationsTable.result],
            reviewResult = reviewAnnotation[AnnotationsTable.result],
        )

        if (isDisputed) {
            annotationStore.markRoundAnnotationsDisputed(itemId, roundNo, now)
            dataItemStore.markItemDisputed(itemId, now)
        } else {
            annotationStore.markRoundAnnotationsAccepted(itemId, roundNo, now)
            dataItemStore.markItemAnnotated(itemId, originalAnnotation[AnnotationsTable.result], now)
        }

        DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
        DatasetQueryHelper.checkAndTransitionToReviewing(datasetId, now)
    }
}

package com.example.api.service.dataset

import com.example.api.db.DatasetsTable
import com.example.api.db.DataItemsTable
import com.example.api.db.AnnotationsTable
import com.example.api.db.AnnotationTaskBatchesTable
import com.example.api.db.AnnotationTasksTable
import com.example.api.models.ClaimTasksResponse
import com.example.api.models.DataItemResponse
import com.example.api.models.DatasetResponse
import com.example.api.models.AnnotatorTaskWorkspaceResponse
import com.example.api.models.AnnotationSubmissionInput
import com.example.api.models.AnnotatorTaskDetailResponse
import com.example.api.models.AnnotatorTaskResponse
import com.example.api.models.SubmitAnnotationBatchResponse
import com.example.api.models.TaskAssignmentResponse
import com.example.api.models.UpdateDatasetResponse
import com.example.api.http.Result
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.count
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
            // 查询所有已开放的数据集，作为标注员可领取任务的候选列表。
            val datasetRows = DatasetsTable
                .selectAll()
                .where { DatasetsTable.status eq "open" }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .toList()

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }
            val pendingItemCount = DataItemsTable.id.count()
            val alreadyAssignedItemIds = if (datasetIds.isEmpty()) {
                emptySet()
            } else {
                AnnotationTasksTable
                    .select(AnnotationTasksTable.itemId)
                    .where {
                        (AnnotationTasksTable.annotatorId eq annotatorId) and
                            (AnnotationTasksTable.datasetId inList datasetIds)
                    }
                    .map { it[AnnotationTasksTable.itemId] }
                    .toSet()
            }

            val pendingCounts = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                // 查询每个开放数据集下当前标注员仍可领取的 pending 数据项数量。
                DataItemsTable
                    .select(DataItemsTable.datasetId, pendingItemCount)
                    .where {
                        (DataItemsTable.datasetId inList datasetIds) and
                            (DataItemsTable.status eq "pending") and
                            if (alreadyAssignedItemIds.isEmpty()) {
                                Op.TRUE
                            } else {
                                DataItemsTable.id notInList alreadyAssignedItemIds
                            }
                    }
                    .groupBy(DataItemsTable.datasetId)
                    .associate { it[DataItemsTable.datasetId] to it[pendingItemCount] }
            }

            val activeByDataset = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                // 查询当前标注员在每个数据集下是否已有活跃任务单。
                AnnotationTaskBatchesTable
                    .selectAll()
                    .where {
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                            (AnnotationTaskBatchesTable.datasetId inList datasetIds) and
                            (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                    }
                    .groupBy { it[AnnotationTaskBatchesTable.datasetId] }
                    .mapValues { it.value.size.toLong() }
            }

            // 查询当前标注员在每个数据集下是否已有互查类型的活跃任务单。
            val activeReviewByDataset = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                AnnotationTaskBatchesTable
                    .selectAll()
                    .where {
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                            (AnnotationTaskBatchesTable.datasetId inList datasetIds) and
                            (AnnotationTaskBatchesTable.batchType eq "review") and
                            (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                    }
                    .groupBy { it[AnnotationTaskBatchesTable.datasetId] }
                    .mapValues { it.value.size.toLong() }
            }

            // 限制同时持有的活跃任务项总数不超过 5 个，避免单用户过度占用系统资源。
            val totalActive = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .sumOf { it[AnnotationTaskBatchesTable.totalCount] }

            // 查询每个数据集下已有原始标注、尚未互查且当前标注员未参与过的数据项数量（互查任务来源）。
            val reviewableCounts = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                findReviewableItemRows(datasetIds, alreadyAssignedItemIds)
                    .groupingBy { it[DataItemsTable.datasetId] }
                    .eachCount()
            }

            // 查询每个数据集中已经形成最终结果的数据项数量。
            val completedCounts = DatasetQueryHelper.countCompletedItems(datasetIds)

            datasetRows.map { row ->
                val datasetId = row[DatasetsTable.id]
                val hasPending = (pendingCounts[datasetId] ?: 0) > 0
                val hasReviewable = (reviewableCounts[datasetId] ?: 0) > 0
                val hasActiveInDataset = (activeByDataset[datasetId] ?: 0) > 0
                val hasActiveReviewInDataset = (activeReviewByDataset[datasetId] ?: 0) > 0
                val canClaimAnnotation = hasPending && !hasActiveInDataset && totalActive < 5
                val canClaimReview = hasReviewable && !hasActiveReviewInDataset && totalActive < 5
                val canClaim = canClaimAnnotation || canClaimReview

                val pendingCount = (pendingCounts[datasetId] ?: 0).toInt()
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
            val dataset = DatasetsTable
                .selectAll()
                .where { DatasetsTable.id eq datasetId }
                .limit(1)
                .firstOrNull()
                ?: return@transaction ClaimTasksTransactionResult.NotFound

            if (dataset[DatasetsTable.status] != "open") {
                return@transaction ClaimTasksTransactionResult.InvalidStatus
            }

            // 查询当前标注员是否已持有该数据集同类型的活跃任务单。
            val existingInDataset = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.datasetId eq datasetId) and
                        (AnnotationTaskBatchesTable.batchType eq taskType) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .count()

            if (existingInDataset > 0) {
                return@transaction ClaimTasksTransactionResult.AlreadyClaimed
            }

            // 查询当前标注员所有活跃任务单的任务项总数，控制并发持有上限。
            val totalActive = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .sumOf { it[AnnotationTaskBatchesTable.totalCount] }

            // 限制同时持有的活跃任务项总数不超过 5 个，避免单用户过度占用系统资源。
            if (totalActive >= 5) {
                return@transaction ClaimTasksTransactionResult.TooManyActive
            }

            val alreadyAssignedItemIds = AnnotationTasksTable
                .select(AnnotationTasksTable.itemId)
                .where {
                    (AnnotationTasksTable.annotatorId eq annotatorId) and
                        (AnnotationTasksTable.datasetId eq datasetId)
                }
                .map { it[AnnotationTasksTable.itemId] }
                .toSet()

            val items = if (taskType == "review") {
                // 互查任务：查询已有原始标注但尚未互查的数据项，排除当前标注员已参与过的。
                findReviewableItemRows(listOf(datasetId), alreadyAssignedItemIds, requestedCount)
            } else {
                // 标注任务：查询 pending 数据项，排除当前标注员已参与过的。
                DataItemsTable
                    .selectAll()
                    .where {
                        (DataItemsTable.datasetId eq datasetId) and
                            (DataItemsTable.status eq "pending") and
                            if (alreadyAssignedItemIds.isEmpty()) {
                                Op.TRUE
                            } else {
                                DataItemsTable.id notInList alreadyAssignedItemIds
                            }
                    }
                    .orderBy(DataItemsTable.createdAt to SortOrder.ASC)
                    .limit(requestedCount)
                    .toList()
            }

            if (items.isEmpty()) {
                return@transaction ClaimTasksTransactionResult.EmptyDataset
            }

            val now = OffsetDateTime.now()
            val claimedItems = if (taskType == "annotation") {
                items.filter { item ->
                    DataItemsTable.update({
                        (DataItemsTable.id eq item[DataItemsTable.id]) and
                            (DataItemsTable.status eq "pending")
                    }) {
                        it[status] = "assigned"
                        it[updatedAt] = now
                    } > 0
                }
            } else {
                items
            }

            if (claimedItems.isEmpty()) {
                return@transaction ClaimTasksTransactionResult.EmptyDataset
            }

            val batchId = UUID.randomUUID()
            val orderNo = generateOrderNo(now, taskType)

            // 创建任务单记录。
            AnnotationTaskBatchesTable.insert {
                it[id] = batchId
                it[AnnotationTaskBatchesTable.orderNo] = orderNo
                it[AnnotationTaskBatchesTable.datasetId] = datasetId
                it[AnnotationTaskBatchesTable.annotatorId] = annotatorId
                it[batchType] = taskType
                it[status] = "assigned"
                it[totalCount] = claimedItems.size
                it[assignedAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }

            val tasks = claimedItems.map { item ->
                val taskId = UUID.randomUUID()

                AnnotationTasksTable.insert {
                    it[id] = taskId
                    it[AnnotationTasksTable.batchId] = batchId
                    it[AnnotationTasksTable.datasetId] = datasetId
                    it[AnnotationTasksTable.itemId] = item[DataItemsTable.id]
                    it[AnnotationTasksTable.annotatorId] = annotatorId
                    it[status] = "assigned"
                    it[assignedAt] = now
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                TaskAssignmentResponse(
                    taskId = taskId.toString(),
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
            ClaimTasksTransactionResult.InvalidStatus -> Result.BadRequest("该数据集未开放领取")
            ClaimTasksTransactionResult.AlreadyClaimed -> Result.BadRequest("该数据集已有未完成任务单")
            ClaimTasksTransactionResult.TooManyActive -> Result.BadRequest("当前进行中的任务已达上限")
            ClaimTasksTransactionResult.EmptyDataset -> Result.BadRequest("该数据集暂无可领取任务")
            is ClaimTasksTransactionResult.Success -> Result.Success(result.value)
        }
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
     * 将数据集表记录转换为响应数据。
     *
     * @param row 数据集表查询结果行
     * @return 数据集响应数据
     */
    private fun toDatasetResponse(
        row: ResultRow,
        canClaim: Boolean? = null,
        pendingItemCount: Int? = null,
        reviewableItemCount: Int? = null,
        completedItemCount: Int? = null,
    ): DatasetResponse {
        return DatasetResponse(
            id = row[DatasetsTable.id].toString(),
            providerId = row[DatasetsTable.providerId].toString(),
            name = row[DatasetsTable.name],
            description = row[DatasetsTable.description],
            annotationGuide = row[DatasetsTable.annotationGuide],
            annotationSchema = row[DatasetsTable.annotationSchema],
            status = row[DatasetsTable.status],
            targetCompletionRatio = row[DatasetsTable.targetCompletionRatio].toPlainString(),
            itemCount = row[DatasetsTable.itemCount],
            completedItemCount = completedItemCount ?: row[DatasetsTable.completedItemCount],
            createdAt = row[DatasetsTable.createdAt].toString(),
            updatedAt = row[DatasetsTable.updatedAt].toString(),
            canClaim = canClaim,
            pendingItemCount = pendingItemCount,
            reviewableItemCount = reviewableItemCount,
            disputedItemCount = null,
        )
    }

    /**
     * 查询可供互查的数据项。
     *
     * 互查候选需同时满足：
     * 1. 已有原始标注（annotation_type = 'annotation' 且 status = 'submitted'）
     * 2. 尚未被互查过（不存在 annotation_type = 'review' 的记录）
     * 3. 排除当前标注员已参与过的数据项
     * 4. 数据项状态为 assigned / annotated / disputed（已有原始标注但尚未完结）
     */
    private fun findReviewableItemRows(
        datasetIds: List<UUID>,
        excludedItemIds: Set<UUID>,
        limit: Int? = null,
    ): List<ResultRow> {
        if (datasetIds.isEmpty()) {
            return emptyList()
        }

        val originalItemIds = AnnotationsTable
            .select(AnnotationsTable.itemId)
            .where {
                (AnnotationsTable.annotationType eq "annotation") and
                    (AnnotationsTable.status eq "submitted")
            }
            .map { it[AnnotationsTable.itemId] }
            .toSet()

        if (originalItemIds.isEmpty()) {
            return emptyList()
        }

        val reviewedItemIds = AnnotationsTable
            .select(AnnotationsTable.itemId)
            .where { AnnotationsTable.annotationType eq "review" }
            .map { it[AnnotationsTable.itemId] }
            .toSet()

        val candidateItemIds = originalItemIds - reviewedItemIds - excludedItemIds
        if (candidateItemIds.isEmpty()) {
            return emptyList()
        }

        val query = DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.id inList candidateItemIds) and
                    (DataItemsTable.status inList listOf("assigned", "annotated", "disputed"))
            }
            .orderBy(DataItemsTable.createdAt to SortOrder.ASC)

        return if (limit == null) {
            query.toList()
        } else {
            query.limit(limit).toList()
        }
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
        // 查询任务单下的全部任务项，作为详情列表的主体数据。
        val taskRows = AnnotationTasksTable
            .selectAll()
            .where {
                (AnnotationTasksTable.annotatorId eq annotatorId) and
                    (AnnotationTasksTable.batchId eq batchId)
            }
            .orderBy(AnnotationTasksTable.assignedAt to SortOrder.ASC)
            .toList()

        val taskIds = taskRows.map { it[AnnotationTasksTable.id] }
        val itemIds = taskRows.map { it[AnnotationTasksTable.itemId] }.toSet()

        val annotationsByTask: Map<UUID, ResultRow> = if (taskIds.isEmpty()) {
            emptyMap()
        } else {
            // 批量查询任务项已有标注结果，用于详情回显。
            AnnotationsTable
                .selectAll()
                .where { AnnotationsTable.taskId inList taskIds }
                .associateBy { it[AnnotationsTable.taskId] }
        }

        val items: Map<UUID, ResultRow> = if (itemIds.isEmpty()) {
            emptyMap()
        } else {
            // 批量查询任务项绑定的数据项内容。
            DataItemsTable
                .selectAll()
                .where { DataItemsTable.id inList itemIds }
                .associateBy { it[DataItemsTable.id] }
        }

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

    private data class BatchTaskRows(
        val taskRows: List<ResultRow>,
        val annotationsByTask: Map<UUID, ResultRow>,
        val items: Map<UUID, ResultRow>,
    )

    private sealed class ClaimTasksTransactionResult {
        data class Success(val value: ClaimTasksResponse) : ClaimTasksTransactionResult()
        data object NotFound : ClaimTasksTransactionResult()
        data object InvalidStatus : ClaimTasksTransactionResult()
        data object AlreadyClaimed : ClaimTasksTransactionResult()
        data object TooManyActive : ClaimTasksTransactionResult()
        data object EmptyDataset : ClaimTasksTransactionResult()
    }

    private sealed class ReturnTaskTransactionResult {
        data object Success : ReturnTaskTransactionResult()
        data object NotFound : ReturnTaskTransactionResult()
        data object InvalidStatus : ReturnTaskTransactionResult()
    }

    private sealed class StartTaskBatchTransactionResult {
        data object Success : StartTaskBatchTransactionResult()
        data object NotFound : StartTaskBatchTransactionResult()
        data object InvalidStatus : StartTaskBatchTransactionResult()
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
                .selectAll()
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
                    .selectAll()
                    .where { DatasetsTable.id inList datasetIds }
                    .associateBy { it[DatasetsTable.id] }
            }

            val tasksByBatch = if (batchIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务单下的任务项，用于统计各状态数量。
                AnnotationTasksTable
                    .selectAll()
                    .where { AnnotationTasksTable.batchId inList batchIds }
                    .groupBy { it[AnnotationTasksTable.batchId] }
            }

            batchRows.mapNotNull { batch ->
                val datasetId = batch[AnnotationTaskBatchesTable.datasetId]
                val dataset = datasets[datasetId] ?: return@mapNotNull null
                val tasks = tasksByBatch[batch[AnnotationTaskBatchesTable.id]].orEmpty()

                AnnotatorTaskResponse(
                    batchId = batch[AnnotationTaskBatchesTable.id].toString(),
                    orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                    datasetId = datasetId.toString(),
                    datasetName = dataset[DatasetsTable.name],
                    status = batch[AnnotationTaskBatchesTable.status],
                    totalCount = batch[AnnotationTaskBatchesTable.totalCount],
                    assignedCount = tasks.count { it[AnnotationTasksTable.status] == "assigned" },
                    inProgressCount = tasks.count { it[AnnotationTasksTable.status] == "in_progress" },
                    submittedCount = tasks.count { it[AnnotationTasksTable.status] == "submitted" },
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
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
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
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
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
                val existingAnnotation = AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId eq submission.taskId }
                    .limit(1)
                    .firstOrNull()

                val isReviewBatch = batch[AnnotationTaskBatchesTable.batchType] == "review"
                val originalAnnotation = if (isReviewBatch) {
                    AnnotationsTable
                        .selectAll()
                        .where {
                            (AnnotationsTable.itemId eq submission.itemId) and
                                (AnnotationsTable.annotationType eq "annotation")
                        }
                        .orderBy(AnnotationsTable.submittedAt to SortOrder.ASC)
                        .limit(1)
                        .firstOrNull()
                        ?: return@transaction SubmitAnnotationBatchResult.InvalidTasks
                } else {
                    null
                }
                // 互查任务需要比对原始标注与互查结果的一致性：
                // - 若原始标注已被标记争议，或互查员主动标记争议，或结果不一致，则视为争议。
                // 标注任务直接使用提交时的争议标记。
                val isDisputed = if (isReviewBatch && originalAnnotation != null) {
                    originalAnnotation[AnnotationsTable.isDisputed] ||
                        submission.isDisputed ||
                        !DatasetQueryHelper.areAnnotationResultsConsistent(
                            originalAnnotation[AnnotationsTable.result],
                            submission.result,
                        )
                } else {
                    submission.isDisputed
                }

                if (existingAnnotation == null) {
                    AnnotationsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[taskId] = submission.taskId
                        it[AnnotationsTable.itemId] = submission.itemId
                        it[AnnotationsTable.annotatorId] = annotatorId
                        it[result] = submission.result
                        it[annotationType] = if (isReviewBatch) "review" else "annotation"
                        if (isReviewBatch) {
                            it[reviewOfAnnotationId] = originalAnnotation?.get(AnnotationsTable.id)
                        }
                        it[comment] = submission.comment
                        it[AnnotationsTable.isDisputed] = isDisputed
                        it[status] = "submitted"
                        it[submittedAt] = now
                        if (isReviewBatch) {
                            it[reviewedAt] = now
                        }
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                } else {
                    AnnotationsTable.update({ AnnotationsTable.taskId eq submission.taskId }) {
                        it[result] = submission.result
                        it[annotationType] = if (isReviewBatch) "review" else "annotation"
                        if (isReviewBatch) {
                            it[reviewOfAnnotationId] = originalAnnotation?.get(AnnotationsTable.id)
                        }
                        it[comment] = submission.comment
                        it[AnnotationsTable.isDisputed] = isDisputed
                        it[status] = "submitted"
                        it[submittedAt] = now
                        if (isReviewBatch) {
                            it[reviewedAt] = now
                        }
                        it[updatedAt] = now
                    }
                }

                AnnotationTasksTable.update({ AnnotationTasksTable.id eq submission.taskId }) {
                    it[status] = "submitted"
                    it[submittedAt] = now
                    it[updatedAt] = now
                }

                // 互查提交完成后，触发数据项状态机推进（一致则采纳，不一致则争议）。
                if (isReviewBatch && originalAnnotation != null) {
                    finalizeReviewedItem(
                        datasetId = taskRow[AnnotationTasksTable.datasetId],
                        itemId = submission.itemId,
                        originalAnnotationId = originalAnnotation[AnnotationsTable.id],
                        now = now,
                    )
                }
            }

            // 查询任务单下已提交的任务项数量，用于判断整单是否完成。
            val submittedCount = AnnotationTasksTable
                .selectAll()
                .where {
                    (AnnotationTasksTable.batchId eq batchId) and
                        (AnnotationTasksTable.status eq "submitted")
                }
                .count()
                .toInt()

            if (batch[AnnotationTaskBatchesTable.status] == "assigned") {
                AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
                    it[status] = "in_progress"
                    if (batch[AnnotationTaskBatchesTable.startedAt] == null) {
                        it[startedAt] = now
                    }
                    it[updatedAt] = now
                }
            }

            if (batch[AnnotationTaskBatchesTable.totalCount] in 1..submittedCount) {
                AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
                    it[status] = "submitted"
                    it[submittedAt] = now
                    it[updatedAt] = now
                }
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


    private data class ParsedSubmission(
        val taskId: UUID,
        val itemId: UUID,
        val result: String,
        val isDisputed: Boolean,
        val comment: String?,
    )

    private sealed class SubmitAnnotationBatchResult {
        data object NotFound : SubmitAnnotationBatchResult()
        data object InvalidStatus : SubmitAnnotationBatchResult()
        data object InvalidTasks : SubmitAnnotationBatchResult()
        data class Success(val submittedCount: Int, val totalCount: Int) : SubmitAnnotationBatchResult()
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
        now: OffsetDateTime,
    ) {
        val annotations = AnnotationsTable
            .selectAll()
            .where {
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }
            .toList()

        val originalAnnotation = annotations.firstOrNull { it[AnnotationsTable.id] == originalAnnotationId } ?: return
        val reviewAnnotation = annotations.firstOrNull { it[AnnotationsTable.annotationType] == "review" } ?: return
        val isDisputed = originalAnnotation[AnnotationsTable.isDisputed] ||
            reviewAnnotation[AnnotationsTable.isDisputed] ||
            !DatasetQueryHelper.areAnnotationResultsConsistent(
                originalAnnotation[AnnotationsTable.result],
                reviewAnnotation[AnnotationsTable.result],
            )

        if (isDisputed) {
            AnnotationsTable.update({
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }) {
                it[AnnotationsTable.isDisputed] = true
                it[status] = "submitted"
                it[adoptionStatus] = 0.toShort()
                it[adoptedAt] = null
                it[adoptedBy] = null
                it[adoptionComment] = null
                it[reviewedAt] = now
                it[updatedAt] = now
            }
            DataItemsTable.update({ DataItemsTable.id eq itemId }) {
                it[status] = "disputed"
                it[finalResult] = null
                it[finalizedAt] = null
                it[finalizedBy] = null
                it[updatedAt] = now
            }
        } else {
            AnnotationsTable.update({
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }) {
                it[AnnotationsTable.isDisputed] = false
                it[status] = "accepted"
                it[adoptionStatus] = 1.toShort()
                it[adoptedAt] = now
                it[adoptedBy] = null
                it[adoptionComment] = "原始标注与互查一致，系统自动采纳"
                it[reviewedAt] = now
                it[updatedAt] = now
            }
            DataItemsTable.update({ DataItemsTable.id eq itemId }) {
                it[status] = "annotated"
                it[finalResult] = originalAnnotation[AnnotationsTable.result]
                it[finalizedAt] = now
                it[finalizedBy] = null
                it[updatedAt] = now
            }
        }

        DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
    }
}

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
import com.example.api.models.AnnotatorTaskResponse
import com.example.api.models.SubmitAnnotationBatchResponse
import com.example.api.models.TaskAssignmentResponse
import com.example.api.models.UpdateDatasetResponse
import com.example.api.service.auth.AuthResult
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

    fun listOpenDatasets(annotatorId: UUID): AuthResult<List<DatasetResponse>> {
        val datasets = transaction {
            // 查询所有已开放的数据集，作为标注员可领取任务的候选列表。
            val datasetRows = DatasetsTable
                .selectAll()
                .where { DatasetsTable.status eq "open" }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .toList()

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }
            val pendingItemCount = DataItemsTable.id.count()

            val pendingCounts = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                // 查询每个开放数据集下仍处于 pending 的数据项数量。
                DataItemsTable
                    .select(DataItemsTable.datasetId, pendingItemCount)
                    .where {
                        (DataItemsTable.datasetId inList datasetIds) and
                            (DataItemsTable.status eq "pending")
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

            // 查询当前标注员所有活跃任务单的任务项总量，用于限制同时持有数量。
            val totalActive = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .sumOf { it[AnnotationTaskBatchesTable.totalCount] }

            // 查询每个数据集下已被标注、且当前标注员未参与过的数据项数量（互查任务来源）。
            val reviewableCounts = if (datasetIds.isEmpty()) {
                emptyMap()
            } else {
                val alreadyReviewedItemIds = AnnotationTasksTable
                    .select(AnnotationTasksTable.itemId)
                    .where {
                        (AnnotationTasksTable.annotatorId eq annotatorId) and
                            (AnnotationTasksTable.datasetId inList datasetIds)
                    }
                    .map { it[AnnotationTasksTable.itemId] }
                    .toSet()

                val reviewableItemCount = DataItemsTable.id.count()
                DataItemsTable
                    .select(DataItemsTable.datasetId, reviewableItemCount)
                    .where {
                        (DataItemsTable.datasetId inList datasetIds) and
                            (DataItemsTable.status inList listOf("annotated", "disputed")) and
                            if (alreadyReviewedItemIds.isEmpty()) {
                                Op.TRUE
                            } else {
                                DataItemsTable.id notInList alreadyReviewedItemIds
                            }
                    }
                    .groupBy(DataItemsTable.datasetId)
                    .associate { it[DataItemsTable.datasetId] to it[reviewableItemCount] }
            }

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
                val reviewableCount = (reviewableCounts[datasetId] ?: 0).toInt()

                toDatasetResponse(
                    row,
                    canClaim = canClaim,
                    pendingItemCount = pendingCount,
                    reviewableItemCount = reviewableCount,
                )
            }
        }

        return AuthResult.Success(datasets)
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
    ): AuthResult<ClaimTasksResponse> {
        if (requestedCount <= 0) {
            return AuthResult.BadRequest("领取数量必须大于 0")
        }
        if (taskType !in listOf("annotation", "review")) {
            return AuthResult.BadRequest("任务类别必须是 annotation 或 review")
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

            if (totalActive >= 5) {
                return@transaction ClaimTasksTransactionResult.TooManyActive
            }

            val items = if (taskType == "review") {
                // 互查任务：查询已被标注的数据项，排除当前标注员已参与过的。
                val alreadyReviewedItemIds = AnnotationTasksTable
                    .select(AnnotationTasksTable.itemId)
                    .where {
                        (AnnotationTasksTable.annotatorId eq annotatorId) and
                            (AnnotationTasksTable.datasetId eq datasetId)
                    }
                    .map { it[AnnotationTasksTable.itemId] }
                    .toSet()

                DataItemsTable
                    .selectAll()
                    .where {
                        (DataItemsTable.datasetId eq datasetId) and
                            (DataItemsTable.status inList listOf("annotated", "disputed")) and
                            (DataItemsTable.id notInList alreadyReviewedItemIds)
                    }
                    .orderBy(DataItemsTable.createdAt to SortOrder.ASC)
                    .limit(requestedCount)
                    .toList()
            } else {
                // 标注任务：查询 pending 数据项。
                DataItemsTable
                    .selectAll()
                    .where {
                        (DataItemsTable.datasetId eq datasetId) and
                            (DataItemsTable.status eq "pending")
                    }
                    .orderBy(DataItemsTable.createdAt to SortOrder.ASC)
                    .limit(requestedCount)
                    .toList()
            }

            if (items.isEmpty()) {
                return@transaction ClaimTasksTransactionResult.EmptyDataset
            }

            val now = OffsetDateTime.now()
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
                it[totalCount] = items.size
                it[assignedAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }

            val tasks = items.map { item ->
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

                // 标注任务才需要改变数据项状态；互查任务保持原状态。
                if (taskType == "annotation") {
                    DataItemsTable.update({ DataItemsTable.id eq item[DataItemsTable.id] }) {
                        it[status] = "assigned"
                        it[updatedAt] = now
                    }
                }

                TaskAssignmentResponse(
                    taskId = taskId.toString(),
                    item = DataItemResponse(
                        id = item[DataItemsTable.id].toString(),
                        datasetId = item[DataItemsTable.datasetId].toString(),
                        content = item[DataItemsTable.content],
                        contentType = item[DataItemsTable.contentType],
                        metadata = item[DataItemsTable.metadata],
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
            ClaimTasksTransactionResult.NotFound -> AuthResult.BadRequest("数据集不存在或无权访问")
            ClaimTasksTransactionResult.InvalidStatus -> AuthResult.BadRequest("该数据集未开放领取")
            ClaimTasksTransactionResult.AlreadyClaimed -> AuthResult.BadRequest("该数据集已有未完成任务单")
            ClaimTasksTransactionResult.TooManyActive -> AuthResult.BadRequest("当前进行中的任务已达上限")
            ClaimTasksTransactionResult.EmptyDataset -> AuthResult.BadRequest("该数据集暂无可领取任务")
            is ClaimTasksTransactionResult.Success -> AuthResult.Success(result.value)
        }
    }

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
            completedItemCount = row[DatasetsTable.completedItemCount],
            createdAt = row[DatasetsTable.createdAt].toString(),
            updatedAt = row[DatasetsTable.updatedAt].toString(),
            canClaim = canClaim,
            pendingItemCount = pendingItemCount,
            reviewableItemCount = reviewableItemCount,
        )
    }

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
     * @param statusFilter 任务单状态筛选，null 时不筛选
     * @return 查询结果，成功时返回任务单列表
     */
    fun listAnnotatorTasks(
        annotatorId: UUID,
        statusFilter: String?,
    ): AuthResult<List<AnnotatorTaskResponse>> {
        val taskBatches = transaction {
            val baseCondition = AnnotationTaskBatchesTable.annotatorId eq annotatorId
            val condition = statusFilter?.let {
                baseCondition and (AnnotationTaskBatchesTable.status eq it)
            } ?: baseCondition

            // 查询当前标注员的任务单列表，可按任务单状态筛选。
            val batchRows = AnnotationTaskBatchesTable
                .selectAll()
                .where { condition }
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

                com.example.api.models.AnnotatorTaskResponse(
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

        return AuthResult.Success(taskBatches)
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
    ): AuthResult<List<com.example.api.models.AnnotatorTaskDetailResponse>> {
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

            val annotationsByTask = if (taskIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务项已有标注结果，用于详情回显。
                AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId inList taskIds }
                    .associateBy { it[AnnotationsTable.taskId] }
            }

            val items = if (itemIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务项绑定的数据项内容。
                DataItemsTable
                    .selectAll()
                    .where { DataItemsTable.id inList itemIds }
                    .associateBy { it[DataItemsTable.id] }
            }

            taskRows.mapNotNull { taskRow ->
                val itemId = taskRow[AnnotationTasksTable.itemId]
                val item = items[itemId] ?: return@mapNotNull null
                val annotation = annotationsByTask[taskRow[AnnotationTasksTable.id]]

                com.example.api.models.AnnotatorTaskDetailResponse(
                    batchId = batchId.toString(),
                    orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                    taskId = taskRow[AnnotationTasksTable.id].toString(),
                    item = DataItemResponse(
                        id = item[DataItemsTable.id].toString(),
                        datasetId = item[DataItemsTable.datasetId].toString(),
                        content = item[DataItemsTable.content],
                        contentType = item[DataItemsTable.contentType],
                        metadata = item[DataItemsTable.metadata],
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
                )
            }
        }

        return if (result == null) {
            AuthResult.BadRequest("任务单不存在或无权访问")
        } else {
            AuthResult.Success(result)
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
    ): AuthResult<UpdateDatasetResponse> {
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
            ReturnTaskTransactionResult.NotFound -> AuthResult.BadRequest("该数据集下没有可退回的任务")
            ReturnTaskTransactionResult.InvalidStatus -> AuthResult.BadRequest("仅可退回已分配或进行中的任务")
            ReturnTaskTransactionResult.Success -> AuthResult.Success(UpdateDatasetResponse("任务已退回"))
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
    ): AuthResult<UpdateDatasetResponse> {
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
            StartTaskBatchTransactionResult.NotFound -> AuthResult.BadRequest("任务单不存在或无权访问")
            StartTaskBatchTransactionResult.InvalidStatus -> AuthResult.BadRequest("仅可开始已领取或进行中的任务单")
            StartTaskBatchTransactionResult.Success -> AuthResult.Success(UpdateDatasetResponse("任务单已开始"))
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
    ): AuthResult<AnnotatorTaskWorkspaceResponse> {
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

            // 查询任务单下的任务项，作为工作台待标注列表。
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

            val annotationsByTask = if (taskIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务项已有标注结果，用于工作台恢复草稿/已提交内容。
                AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId inList taskIds }
                    .associateBy { it[AnnotationsTable.taskId] }
            }

            val items = if (itemIds.isEmpty()) {
                emptyMap()
            } else {
                // 批量查询任务项绑定的数据项内容，避免在循环内查询。
                DataItemsTable
                    .selectAll()
                    .where { DataItemsTable.id inList itemIds }
                    .associateBy { it[DataItemsTable.id] }
            }

            val tasks = taskRows.mapNotNull { taskRow ->
                val itemId = taskRow[AnnotationTasksTable.itemId]
                val item = items[itemId] ?: return@mapNotNull null
                val annotation = annotationsByTask[taskRow[AnnotationTasksTable.id]]

                com.example.api.models.AnnotatorTaskDetailResponse(
                    batchId = batchId.toString(),
                    orderNo = batch[AnnotationTaskBatchesTable.orderNo],
                    taskId = taskRow[AnnotationTasksTable.id].toString(),
                    item = DataItemResponse(
                        id = item[DataItemsTable.id].toString(),
                        datasetId = item[DataItemsTable.datasetId].toString(),
                        content = item[DataItemsTable.content],
                        contentType = item[DataItemsTable.contentType],
                        metadata = item[DataItemsTable.metadata],
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
                )
            }

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
            AuthResult.BadRequest("任务单不存在或无权访问")
        } else {
            AuthResult.Success(result)
        }
    }

    /**
     * 批量提交标注员在任务单下的标注结果。
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
    ): AuthResult<SubmitAnnotationBatchResponse> {
        if (submissions.isEmpty()) {
            return AuthResult.BadRequest("提交内容不能为空")
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
            return AuthResult.BadRequest("提交内容格式不正确")
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

                if (existingAnnotation == null) {
                    AnnotationsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[taskId] = submission.taskId
                        it[AnnotationsTable.itemId] = submission.itemId
                        it[AnnotationsTable.annotatorId] = annotatorId
                        it[result] = submission.result
                        if (isReviewBatch) {
                            it[reviewResult] = submission.result
                        }
                        it[comment] = submission.comment
                        it[isDisputed] = submission.isDisputed
                        it[status] = "submitted"
                        it[submittedAt] = now
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                } else {
                    AnnotationsTable.update({ AnnotationsTable.taskId eq submission.taskId }) {
                        it[result] = submission.result
                        if (isReviewBatch) {
                            it[reviewResult] = submission.result
                        }
                        it[comment] = submission.comment
                        it[isDisputed] = submission.isDisputed
                        it[status] = "submitted"
                        it[submittedAt] = now
                        it[updatedAt] = now
                    }
                }

                AnnotationTasksTable.update({ AnnotationTasksTable.id eq submission.taskId }) {
                    it[status] = "submitted"
                    it[submittedAt] = now
                    it[updatedAt] = now
                }

                // 标注任务提交时更新数据项状态；互查任务不改变原数据项状态。
                if (batch[AnnotationTaskBatchesTable.batchType] != "review") {
                    DataItemsTable.update({ DataItemsTable.id eq submission.itemId }) {
                        it[status] = if (submission.isDisputed) "disputed" else "annotated"
                        it[updatedAt] = now
                    }
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
            SubmitAnnotationBatchResult.NotFound -> AuthResult.BadRequest("任务单不存在或无权访问")
            SubmitAnnotationBatchResult.InvalidStatus -> AuthResult.BadRequest("仅可提交已领取或进行中的任务单")
            SubmitAnnotationBatchResult.InvalidTasks -> AuthResult.BadRequest("任务内容不匹配或无权访问")
            is SubmitAnnotationBatchResult.Success -> AuthResult.Success(
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
}

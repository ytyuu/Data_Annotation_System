package com.example.api.service.dataset

import com.example.api.db.DatasetsTable
import com.example.api.db.DataItemsTable
import com.example.api.db.AnnotationsTable
import com.example.api.db.AnnotationTaskBatchesTable
import com.example.api.db.AnnotationTasksTable
import com.example.api.models.CreateDatasetRequest
import com.example.api.models.ClaimTasksResponse
import com.example.api.models.DataItemResponse
import com.example.api.models.DeleteDataItemResponse
import com.example.api.models.DeleteDatasetResponse
import com.example.api.models.DatasetResponse
import com.example.api.models.AnnotatorTaskWorkspaceResponse
import com.example.api.models.AnnotationSubmissionInput
import com.example.api.models.ImportDataItemsRequest
import com.example.api.models.ImportDataItemsResponse
import com.example.api.models.PublishDatasetResponse
import com.example.api.models.SubmitAnnotationBatchResponse
import com.example.api.models.TaskAssignmentResponse
import com.example.api.models.UpdateDatasetRequest
import com.example.api.models.UpdateDatasetResponse
import com.example.api.service.auth.AuthResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 数据集业务服务，封装提供者数据集创建和查询逻辑。
 */
class DatasetService {
    private val objectMapper = ObjectMapper()
    private val supportedContentTypes = setOf("text", "image", "audio", "video", "json")
    private val activeTaskStatuses = listOf("assigned", "in_progress")
    private val activeBatchStatuses = listOf("assigned", "in_progress")

    /**
     * 为指定数据集提供者创建草稿数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param request 创建数据集请求
     * @return 创建结果，成功时返回数据集响应数据
     */
    fun createProviderDataset(
        providerId: UUID,
        request: CreateDatasetRequest,
    ): AuthResult<DatasetResponse> {
        val name = request.name.trim()
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val annotationGuide = request.annotationGuide?.trim()?.takeIf { it.isNotEmpty() }
        val annotationSchema = request.annotationSchema.trim().ifEmpty { "{}" }
        val targetCompletionRatio = request.targetCompletionRatio.trim().ifEmpty { "50.00" }

        val validationError = validateDataset(name, annotationSchema, targetCompletionRatio)
        if (validationError != null) {
            return AuthResult.BadRequest(validationError)
        }

        val ratio = targetCompletionRatio.toBigDecimal()

        return try {
            val dataset = transaction {
                val now = OffsetDateTime.now()
                val datasetId = UUID.randomUUID()

                DatasetsTable.insert {
                    it[id] = datasetId
                    it[DatasetsTable.providerId] = providerId
                    it[DatasetsTable.name] = name
                    it[DatasetsTable.description] = description
                    it[DatasetsTable.annotationGuide] = annotationGuide
                    it[DatasetsTable.annotationSchema] = annotationSchema
                    it[status] = "draft"
                    it[DatasetsTable.targetCompletionRatio] = ratio
                    it[itemCount] = 0
                    it[completedItemCount] = 0
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                DatasetResponse(
                    id = datasetId.toString(),
                    providerId = providerId.toString(),
                    name = name,
                    description = description,
                    annotationGuide = annotationGuide,
                    annotationSchema = annotationSchema,
                    status = "draft",
                    targetCompletionRatio = ratio.toPlainString(),
                    itemCount = 0,
                    completedItemCount = 0,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )
            }

            AuthResult.Success(dataset)
        } catch (error: ExposedSQLException) {
            AuthResult.BadRequest("数据集信息不符合数据库约束")
        }
    }

    /**
     * 查询指定数据集提供者创建的数据集列表。
     *
     * @param providerId 数据集提供者用户 ID
     * @return 查询结果，成功时返回按更新时间倒序排列的数据集列表
     */
    fun listProviderDatasets(providerId: UUID): AuthResult<List<DatasetResponse>> {
        val datasets = transaction {
            DatasetsTable
                .selectAll()
                .where { DatasetsTable.providerId eq providerId }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .map(::toDatasetResponse)
        }

        return AuthResult.Success(datasets)
    }

    /**
     * 查询所有对标注员开放的数据集列表。
     *
     * @param annotatorId 标注员用户 ID
     * @return 查询结果，成功时返回按更新时间倒序排列的数据集列表
     */
    fun listOpenDatasets(annotatorId: UUID): AuthResult<List<DatasetResponse>> {
        val datasets = transaction {
            val datasetRows = DatasetsTable
                .selectAll()
                .where { DatasetsTable.status eq "open" }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .toList()

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }
            val pendingItemCount = DataItemsTable.id.count()

            val pendingCounts = if (datasetIds.isEmpty()) {
                emptyMap<UUID, Long>()
            } else {
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
                emptyMap<UUID, Long>()
            } else {
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

            val totalActive = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .sumOf { it[AnnotationTaskBatchesTable.totalCount] }

            datasetRows.map { row ->
                val datasetId = row[DatasetsTable.id]
                val hasPending = (pendingCounts[datasetId] ?: 0) > 0
                val hasActiveInDataset = (activeByDataset[datasetId] ?: 0) > 0
                val canClaim = hasPending && !hasActiveInDataset && totalActive < 5

                toDatasetResponse(row, canClaim)
            }
        }

        return AuthResult.Success(datasets)
    }

    /**
     * 为标注员领取指定数据集的待标注任务。
     *
     * @param annotatorId 标注员用户 ID
     * @param datasetId 数据集 ID
     * @param requestedCount 期望领取的任务数量
     * @return 领取结果
     */
    fun claimAnnotatorTasks(
        annotatorId: UUID,
        datasetId: UUID,
        requestedCount: Int,
    ): AuthResult<ClaimTasksResponse> {
        if (requestedCount <= 0) {
            return AuthResult.BadRequest("领取数量必须大于 0")
        }

        val result = transaction {
            val dataset = DatasetsTable
                .selectAll()
                .where { DatasetsTable.id eq datasetId }
                .limit(1)
                .firstOrNull()
                ?: return@transaction ClaimTasksTransactionResult.NotFound

            if (dataset[DatasetsTable.status] != "open") {
                return@transaction ClaimTasksTransactionResult.InvalidStatus
            }

            val existingInDataset = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.datasetId eq datasetId) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                }
                .count()

            if (existingInDataset > 0) {
                return@transaction ClaimTasksTransactionResult.AlreadyClaimed
            }

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

            val pendingItems = DataItemsTable
                .selectAll()
                .where {
                    (DataItemsTable.datasetId eq datasetId) and
                        (DataItemsTable.status eq "pending")
                }
                .orderBy(DataItemsTable.createdAt to SortOrder.ASC)
                .limit(requestedCount)
                .toList()

            if (pendingItems.isEmpty()) {
                return@transaction ClaimTasksTransactionResult.EmptyDataset
            }

            val now = OffsetDateTime.now()
            val batchId = UUID.randomUUID()
            val orderNo = generateOrderNo(now)

            AnnotationTaskBatchesTable.insert {
                it[id] = batchId
                it[AnnotationTaskBatchesTable.orderNo] = orderNo
                it[AnnotationTaskBatchesTable.datasetId] = datasetId
                it[AnnotationTaskBatchesTable.annotatorId] = annotatorId
                it[status] = "assigned"
                it[totalCount] = pendingItems.size
                it[assignedAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }

            val tasks = pendingItems.map { item ->
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

                DataItemsTable.update({ DataItemsTable.id eq item[DataItemsTable.id] }) {
                    it[status] = "assigned"
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
                        status = "assigned",
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

    /**
     * 向指定提供者的数据集批量导入数据项。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param request 批量导入请求
     * @return 导入结果，成功时返回导入数量和最新数据项总数
     */
    fun importDataItems(
        providerId: UUID,
        datasetId: UUID,
        request: ImportDataItemsRequest,
    ): AuthResult<ImportDataItemsResponse> {
        val items = request.items.map { item ->
            item.copy(
                content = item.content.trim(),
                contentType = item.contentType.trim().ifEmpty { "text" },
                metadata = item.metadata.trim().ifEmpty { "{}" },
            )
        }

        val validationError = validateDataItems(items)
        if (validationError != null) {
            return AuthResult.BadRequest(validationError)
        }

        return try {
            val response = transaction {
                val dataset = findProviderDataset(providerId, datasetId)
                    ?: return@transaction null

                if (dataset.status != "draft") {
                    return@transaction ImportDataItemsTransactionResult.InvalidStatus
                }

                val now = OffsetDateTime.now()

                items.forEach { item ->
                    DataItemsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[DataItemsTable.datasetId] = datasetId
                        it[content] = item.content
                        it[contentType] = item.contentType
                        it[metadata] = item.metadata
                        it[status] = "pending"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                val itemCount = DataItemsTable
                    .selectAll()
                    .where { DataItemsTable.datasetId eq datasetId }
                    .count()
                    .toInt()

                DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                    it[DatasetsTable.itemCount] = itemCount
                    it[updatedAt] = now
                }

                ImportDataItemsTransactionResult.Success(
                    ImportDataItemsResponse(
                        importedCount = items.size,
                        itemCount = itemCount,
                    )
                )
            }

            when (response) {
                null -> AuthResult.BadRequest("数据集不存在或无权访问")
                ImportDataItemsTransactionResult.InvalidStatus -> AuthResult.BadRequest("只能向草稿状态的数据集导入数据项")
                is ImportDataItemsTransactionResult.Success -> AuthResult.Success(response.value)
            }
        } catch (error: ExposedSQLException) {
            AuthResult.Conflict("数据项导入失败，请检查外部编号是否重复")
        }
    }

    /**
     * 查询指定提供者数据集下的数据项列表。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 查询结果，成功时返回按创建时间倒序排列的数据项列表
     */
    fun listProviderDataItems(providerId: UUID, datasetId: UUID): AuthResult<List<DataItemResponse>> {
        val items = transaction {
            findProviderDataset(providerId, datasetId)
                ?: return@transaction null

            DataItemsTable
                .selectAll()
                .where { DataItemsTable.datasetId eq datasetId }
                .orderBy(DataItemsTable.createdAt to SortOrder.DESC)
                .map(::toDataItemResponse)
        }

        return if (items == null) {
            AuthResult.BadRequest("数据集不存在或无权访问")
        } else {
            AuthResult.Success(items)
        }
    }

    /**
     * 删除指定提供者草稿数据集下的数据项。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param itemId 数据项 ID
     * @return 删除结果
     */
    fun deleteProviderDataItem(
        providerId: UUID,
        datasetId: UUID,
        itemId: UUID,
    ): AuthResult<DeleteDataItemResponse> {
        val result = transaction {
            val dataset = findProviderDataset(providerId, datasetId)
                ?: return@transaction DeleteDataItemTransactionResult.NotFound

            if (dataset.status != "draft") {
                return@transaction DeleteDataItemTransactionResult.InvalidStatus
            }

            val deletedCount = DataItemsTable.deleteWhere {
                (DataItemsTable.id eq itemId) and (DataItemsTable.datasetId eq datasetId)
            }

            if (deletedCount == 0) {
                return@transaction DeleteDataItemTransactionResult.NotFound
            }

            DeleteDataItemTransactionResult.Success(refreshDatasetItemCount(datasetId))
        }

        return when (result) {
            DeleteDataItemTransactionResult.NotFound -> AuthResult.BadRequest("数据项不存在或无权访问")
            DeleteDataItemTransactionResult.InvalidStatus -> AuthResult.BadRequest("只能删除草稿状态数据集的数据项")
            is DeleteDataItemTransactionResult.Success -> AuthResult.Success(
                DeleteDataItemResponse(
                    message = "数据项已删除",
                    itemCount = result.itemCount,
                )
            )
        }
    }

    /**
     * 更新指定提供者的草稿数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param request 更新数据集请求
     * @return 更新结果
     */
    fun updateProviderDataset(
        providerId: UUID,
        datasetId: UUID,
        request: UpdateDatasetRequest,
    ): AuthResult<UpdateDatasetResponse> {
        val name = request.name.trim()
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val annotationGuide = request.annotationGuide?.trim()?.takeIf { it.isNotEmpty() }
        val annotationSchema = request.annotationSchema.trim().ifEmpty { "{}" }
        val targetCompletionRatio = request.targetCompletionRatio.trim().ifEmpty { "50.00" }

        val validationError = validateDataset(name, annotationSchema, targetCompletionRatio)
        if (validationError != null) {
            return AuthResult.BadRequest(validationError)
        }

        val ratio = targetCompletionRatio.toBigDecimal()

        val result = transaction {
            val dataset = findProviderDataset(providerId, datasetId)
                ?: return@transaction UpdateDatasetTransactionResult.NotFound

            if (dataset.status != "draft") {
                return@transaction UpdateDatasetTransactionResult.InvalidStatus
            }

            val now = OffsetDateTime.now()

            DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                it[DatasetsTable.name] = name
                it[DatasetsTable.description] = description
                it[DatasetsTable.annotationGuide] = annotationGuide
                it[DatasetsTable.annotationSchema] = annotationSchema
                it[DatasetsTable.targetCompletionRatio] = ratio
                it[updatedAt] = now
            }

            UpdateDatasetTransactionResult.Success
        }

        return when (result) {
            UpdateDatasetTransactionResult.NotFound -> AuthResult.BadRequest("数据集不存在或无权访问")
            UpdateDatasetTransactionResult.InvalidStatus -> AuthResult.BadRequest("只能修改草稿状态的数据集")
            UpdateDatasetTransactionResult.Success -> AuthResult.Success(UpdateDatasetResponse("数据集已更新"))
        }
    }

    /**
     * 发布指定提供者的草稿数据集，使其对标注员开放。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 发布结果
     */
    fun publishProviderDataset(providerId: UUID, datasetId: UUID): AuthResult<PublishDatasetResponse> {
        val result = transaction {
            val dataset = findProviderDataset(providerId, datasetId)
                ?: return@transaction PublishDatasetTransactionResult.NotFound

            if (dataset.status != "draft") {
                return@transaction PublishDatasetTransactionResult.InvalidStatus
            }

            val itemCount = DataItemsTable
                .selectAll()
                .where { DataItemsTable.datasetId eq datasetId }
                .count()
                .toInt()

            if (itemCount <= 0) {
                return@transaction PublishDatasetTransactionResult.EmptyDataset
            }

            DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                it[status] = "open"
                it[DatasetsTable.itemCount] = itemCount
                it[updatedAt] = OffsetDateTime.now()
            }

            PublishDatasetTransactionResult.Success
        }

        return when (result) {
            PublishDatasetTransactionResult.NotFound -> AuthResult.BadRequest("数据集不存在或无权访问")
            PublishDatasetTransactionResult.InvalidStatus -> AuthResult.BadRequest("只能发布草稿状态的数据集")
            PublishDatasetTransactionResult.EmptyDataset -> AuthResult.BadRequest("发布前请先导入至少 1 条数据项")
            PublishDatasetTransactionResult.Success -> AuthResult.Success(
                PublishDatasetResponse(
                    message = "数据集已发布",
                    status = "open",
                )
            )
        }
    }

    /**
     * 删除指定提供者的草稿数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 删除结果
     */
    fun deleteProviderDataset(providerId: UUID, datasetId: UUID): AuthResult<DeleteDatasetResponse> {
        val result = transaction {
            val dataset = findProviderDataset(providerId, datasetId)
                ?: return@transaction DeleteDatasetTransactionResult.NotFound

            if (dataset.status != "draft") {
                return@transaction DeleteDatasetTransactionResult.InvalidStatus
            }

            DatasetsTable.deleteWhere {
                (DatasetsTable.id eq datasetId) and (DatasetsTable.providerId eq providerId)
            }

            DeleteDatasetTransactionResult.Success
        }

        return when (result) {
            DeleteDatasetTransactionResult.NotFound -> AuthResult.BadRequest("数据集不存在或无权访问")
            DeleteDatasetTransactionResult.InvalidStatus -> AuthResult.BadRequest("只能删除草稿状态的数据集")
            DeleteDatasetTransactionResult.Success -> AuthResult.Success(DeleteDatasetResponse("数据集已删除"))
        }
    }

    private fun generateOrderNo(now: OffsetDateTime): String {
        val timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val suffix = UUID.randomUUID().toString().take(6).uppercase()
        return "TASK-${timestamp}-${suffix}"
    }

    /**
     * 校验创建数据集请求中的业务字段。
     *
     * @param name 数据集名称
     * @param annotationSchema 标注结构 JSON 字符串
     * @param targetCompletionRatio 触发审核的目标完成比例
     * @return 校验失败时返回错误消息，校验通过返回 null
     */
    private fun validateDataset(
        name: String,
        annotationSchema: String,
        targetCompletionRatio: String,
    ): String? {
        val ratio = targetCompletionRatio.toBigDecimalOrNull()

        return when {
            name.length !in 1..120 -> "数据集名称长度必须为 1 到 120 个字符"
            !isJsonObject(annotationSchema) -> "标注结构必须是合法的 JSON 对象"
            ratio == null -> "目标完成比例必须是数字"
            ratio <= BigDecimal.ZERO || ratio > BigDecimal("100") -> "目标完成比例必须大于 0 且不超过 100"
            else -> null
        }
    }

    /**
     * 校验批量导入的数据项。
     *
     * @param items 待导入的数据项列表
     * @return 校验失败时返回错误消息，校验通过返回 null
     */
    private fun validateDataItems(items: List<com.example.api.models.DataItemInput>): String? {
        return when {
            items.isEmpty() -> "请至少导入 1 条数据项"
            items.size > 500 -> "单次最多导入 500 条数据项"
            items.any { it.content.isEmpty() } -> "数据项内容不能为空"
            items.any { it.contentType !in supportedContentTypes } -> "数据项内容类型不支持"
            items.any { !isJsonObject(it.metadata) } -> "数据项扩展信息必须是合法的 JSON 对象"
            else -> null
        }
    }

    /**
     * 重新计算并写回数据集的数据项总数。
     *
     * @param datasetId 数据集 ID
     * @return 最新数据项总数
     */
    private fun refreshDatasetItemCount(datasetId: UUID): Int {
        val itemCount = DataItemsTable
            .selectAll()
            .where { DataItemsTable.datasetId eq datasetId }
            .count()
            .toInt()

        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[DatasetsTable.itemCount] = itemCount
            it[updatedAt] = OffsetDateTime.now()
        }

        return itemCount
    }

    /**
     * 将数据项表记录转换为响应数据。
     *
     * @param row 数据项表查询结果行
     * @return 数据项响应数据
     */
    private fun toDataItemResponse(row: ResultRow): DataItemResponse {
        return DataItemResponse(
            id = row[DataItemsTable.id].toString(),
            datasetId = row[DataItemsTable.datasetId].toString(),
            content = row[DataItemsTable.content],
            contentType = row[DataItemsTable.contentType],
            metadata = row[DataItemsTable.metadata],
            status = row[DataItemsTable.status],
            createdAt = row[DataItemsTable.createdAt].toString(),
            updatedAt = row[DataItemsTable.updatedAt].toString(),
        )
    }

    /**
     * 将数据集表记录转换为响应数据。
     *
     * @param row 数据集表查询结果行
     * @return 数据集响应数据
     */
    private fun toDatasetResponse(row: ResultRow, canClaim: Boolean? = null): DatasetResponse {
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
        )
    }

    /**
     * 查找指定提供者拥有的数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 查找到的数据集状态信息，不存在时返回 null
     */
    private fun findProviderDataset(providerId: UUID, datasetId: UUID): ProviderDatasetRecord? {
        return DatasetsTable
            .selectAll()
            .where {
                (DatasetsTable.id eq datasetId) and (DatasetsTable.providerId eq providerId)
            }
            .limit(1)
            .firstOrNull()
            ?.let { row ->
                ProviderDatasetRecord(
                    id = row[DatasetsTable.id],
                    status = row[DatasetsTable.status],
                )
            }
    }

    /**
     * 判断输入字符串是否为 JSON 对象。
     *
     * @param value 待校验的 JSON 字符串
     * @return 是 JSON 对象时返回 true
     */
    private fun isJsonObject(value: String): Boolean {
        return runCatching { objectMapper.readTree(value)?.isObject == true }.getOrDefault(false)
    }

    private data class ProviderDatasetRecord(
        val id: UUID,
        val status: String,
    )

    private sealed class ImportDataItemsTransactionResult {
        data class Success(val value: ImportDataItemsResponse) : ImportDataItemsTransactionResult()
        data object InvalidStatus : ImportDataItemsTransactionResult()
    }

    private enum class DeleteDatasetTransactionResult {
        Success,
        NotFound,
        InvalidStatus,
    }

    private enum class UpdateDatasetTransactionResult {
        Success,
        NotFound,
        InvalidStatus,
    }

    private enum class PublishDatasetTransactionResult {
        Success,
        NotFound,
        InvalidStatus,
        EmptyDataset,
    }

    private sealed class DeleteDataItemTransactionResult {
        data class Success(val itemCount: Int) : DeleteDataItemTransactionResult()
        data object NotFound : DeleteDataItemTransactionResult()
        data object InvalidStatus : DeleteDataItemTransactionResult()
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
    ): AuthResult<List<com.example.api.models.AnnotatorTaskResponse>> {
        val taskBatches = transaction {
            val baseCondition = AnnotationTaskBatchesTable.annotatorId eq annotatorId
            val condition = statusFilter?.let {
                baseCondition and (AnnotationTaskBatchesTable.status eq it)
            } ?: baseCondition

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
                DatasetsTable
                    .selectAll()
                    .where { DatasetsTable.id inList datasetIds }
                    .associateBy { it[DatasetsTable.id] }
            }

            val tasksByBatch = if (batchIds.isEmpty()) {
                emptyMap()
            } else {
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
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction null

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
                AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId inList taskIds }
                    .associateBy { it[AnnotationsTable.taskId] }
            }

            val items = if (itemIds.isEmpty()) {
                emptyMap()
            } else {
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
            tasks.forEach { task ->
                val taskId = task[AnnotationTasksTable.id]
                val itemId = task[AnnotationTasksTable.itemId]

                AnnotationTasksTable.update({ AnnotationTasksTable.id eq taskId }) {
                    it[status] = "cancelled"
                    it[updatedAt] = now
                }

                DataItemsTable.update({ DataItemsTable.id eq itemId }) {
                    it[status] = "pending"
                    it[updatedAt] = now
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
            val batch = AnnotationTaskBatchesTable
                .selectAll()
                .where {
                    (AnnotationTaskBatchesTable.id eq batchId) and
                        (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction null

            val dataset = DatasetsTable
                .selectAll()
                .where { DatasetsTable.id eq batch[AnnotationTaskBatchesTable.datasetId] }
                .limit(1)
                .firstOrNull()
                ?: return@transaction null

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
                AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId inList taskIds }
                    .associateBy { it[AnnotationsTable.taskId] }
            }

            val items = if (itemIds.isEmpty()) {
                emptyMap()
            } else {
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

                val existingAnnotation = AnnotationsTable
                    .selectAll()
                    .where { AnnotationsTable.taskId eq submission.taskId }
                    .limit(1)
                    .firstOrNull()

                if (existingAnnotation == null) {
                    AnnotationsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[taskId] = submission.taskId
                        it[AnnotationsTable.itemId] = submission.itemId
                        it[AnnotationsTable.annotatorId] = annotatorId
                        it[result] = submission.result
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

                DataItemsTable.update({ DataItemsTable.id eq submission.itemId }) {
                    it[status] = if (submission.isDisputed) "disputed" else "annotated"
                    it[updatedAt] = now
                }
            }

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

package com.example.api.service.dataset

import com.example.api.db.DatasetsTable
import com.example.api.db.DataItemsTable
import com.example.api.models.CreateDatasetRequest
import com.example.api.models.DataItemResponse
import com.example.api.models.DeleteDataItemResponse
import com.example.api.models.DeleteDatasetResponse
import com.example.api.models.DatasetResponse
import com.example.api.models.ImportDataItemsRequest
import com.example.api.models.ImportDataItemsResponse
import com.example.api.models.PublishDatasetResponse
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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 数据集提供者业务服务，封装提供者侧数据集和数据项管理逻辑。
 */
class ProviderDatasetService {
    private val objectMapper = ObjectMapper()
    private val supportedContentTypes = setOf("text", "image", "audio", "video", "json")

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
        } catch (_: ExposedSQLException) {
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
            // 查询当前提供者创建的数据集，并按最近更新时间倒序展示。
            val datasetRows = DatasetsTable
                .selectAll()
                .where { DatasetsTable.providerId eq providerId }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .toList()

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }

            // 查询每个数据集中同时有 annotation 和 review 且 is_disputed=false 的数据项数量。
            val completedCounts = DatasetQueryHelper.countFullyAnnotatedItems(datasetIds)

            datasetRows.map { row ->
                toDatasetResponse(
                    row,
                    completedItemCount = completedCounts[row[DatasetsTable.id]] ?: 0,
                )
            }
        }

        return AuthResult.Success(datasets)
    }

    /**
     * 向指定数据集批量导入数据项。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param request 导入请求，包含待导入的数据项列表
     * @return 导入结果
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

                // 插入数据项
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

                // 重新查询数据集的数据项总数，并同步写回 datasets.item_count。
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
        } catch (_: ExposedSQLException) {
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

            // 查询指定数据集下的全部数据项，供提供者查看导入内容。
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

            // 查询数据集的数据项总数，发布前必须确保至少有一条数据。
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
        // 查询数据集当前实际数据项数量，用于删除后刷新冗余计数字段。
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
    private fun toDatasetResponse(
        row: ResultRow,
        canClaim: Boolean? = null,
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
        // 查询提供者名下的指定数据集，用于校验归属和读取状态。
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

}

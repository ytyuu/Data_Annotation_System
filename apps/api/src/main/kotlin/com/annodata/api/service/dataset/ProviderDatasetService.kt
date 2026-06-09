package com.annodata.api.service.dataset

import com.annodata.api.db.AnnotationsTable
import com.annodata.api.db.DatasetsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.DatasetReviewsTable
import com.annodata.api.models.AnnotationDetailResponse
import com.annodata.api.models.CreateDatasetRequest
import com.annodata.api.models.DataItemResponse
import com.annodata.api.models.DeleteDataItemResponse
import com.annodata.api.models.DeleteDatasetResponse
import com.annodata.api.models.DisputedItemDetailResponse
import com.annodata.api.models.DatasetResponse
import com.annodata.api.models.ImportDataItemsRequest
import com.annodata.api.models.ImportDataItemsResponse
import com.annodata.api.models.PublishDatasetResponse
import com.annodata.api.models.ResolveDisputeRequest
import com.annodata.api.models.ReviewDetailResponse
import com.annodata.api.models.ReviewItemResponse
import com.annodata.api.models.SubmitReviewRequest
import com.annodata.api.models.SubmitReviewResponse
import com.annodata.api.models.FinishReviewRequest
import com.annodata.api.models.FinishReviewResponse
import com.annodata.api.models.UpdateDatasetRequest
import com.annodata.api.models.UpdateDatasetResponse
import com.annodata.api.http.Result
import com.annodata.api.service.dataset.policy.DatasetStatusPolicy
import com.annodata.api.service.dataset.result.CompleteReviewTransactionResult
import com.annodata.api.service.dataset.result.DeleteDataItemTransactionResult
import com.annodata.api.service.dataset.result.DeleteDatasetTransactionResult
import com.annodata.api.service.dataset.result.DisputeDetailResult
import com.annodata.api.service.dataset.result.FinishReviewTransactionResult
import com.annodata.api.service.dataset.result.ImportDataItemsTransactionResult
import com.annodata.api.service.dataset.result.PublishDatasetTransactionResult
import com.annodata.api.service.dataset.result.RepublishRejectedItemsResult
import com.annodata.api.service.dataset.result.ResolveDisputeTransactionResult
import com.annodata.api.service.dataset.result.ReviewItemTransactionResult
import com.annodata.api.service.dataset.result.ReviewItemsResult
import com.annodata.api.service.dataset.result.SubmitReviewTransactionResult
import com.annodata.api.service.dataset.result.UpdateDatasetTransactionResult
import com.annodata.api.service.dataset.store.AnnotationStore
import com.annodata.api.service.dataset.store.DataItemStore
import com.annodata.api.service.dataset.store.DatasetReviewStore
import com.annodata.api.service.dataset.store.DatasetStore
import com.annodata.api.service.dataset.view.toDataItemResponse
import com.annodata.api.service.dataset.view.toDatasetResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
    private val datasetStore = DatasetStore()
    private val dataItemStore = DataItemStore()
    private val annotationStore = AnnotationStore()
    private val datasetReviewStore = DatasetReviewStore()
    private val objectMapper = ObjectMapper()
    private val supportedContentTypes = setOf("text", "image", "audio", "video", "json")

    /**
     * 创建新的草稿数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param request 创建数据集请求，包含名称、描述、标注结构等信息
     * @return 创建结果，成功时返回新建的数据集信息
     */
    fun createProviderDataset(
        providerId: UUID,
        request: CreateDatasetRequest,
    ): Result<DatasetResponse> {
        val name = request.name.trim()
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val annotationGuide = request.annotationGuide?.trim()?.takeIf { it.isNotEmpty() }
        val annotationSchema = request.annotationSchema.trim().ifEmpty { "{}" }
        val targetCompletionRatio = request.targetCompletionRatio.trim().ifEmpty { "50.00" }

        val validationError = validateDataset(name, annotationSchema, targetCompletionRatio)
        if (validationError != null) {
            return Result.BadRequest(validationError)
        }

        val ratio = targetCompletionRatio.toBigDecimal()

        return try {
            val dataset = transaction {
                val now = OffsetDateTime.now()
                val datasetId = UUID.randomUUID()

                datasetStore.insertDraftDataset(
                    providerId = providerId,
                    datasetId = datasetId,
                    name = name,
                    description = description,
                    annotationGuide = annotationGuide,
                    annotationSchema = annotationSchema,
                    targetCompletionRatio = ratio,
                    now = now,
                )

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
                    hasBeenReviewed = false,
                )
            }

            Result.Success(dataset)
        } catch (_: ExposedSQLException) {
            Result.BadRequest("数据集信息不符合数据库约束")
        }
    }

    /**
     * 查询指定数据集提供者创建的数据集列表。
     *
     * @param providerId 数据集提供者用户 ID
     * @return 查询结果，成功时返回按更新时间倒序排列的数据集列表
     */
    fun listProviderDatasets(providerId: UUID): Result<List<DatasetResponse>> {
        val datasets = transaction {
            // 查询当前提供者创建的数据集，并按最近更新时间倒序展示。
            val datasetRows = datasetStore.listProviderDatasetRows(providerId)

            val datasetIds = datasetRows.map { it[DatasetsTable.id] }

            // 查询每个数据集中已经形成最终结果的数据项数量。
            val completedCounts = DatasetQueryHelper.countCompletedItems(datasetIds)

            // 查询每个数据集中处于争议状态的数据项数量。
            val disputedCounts = DatasetQueryHelper.countDisputedItems(datasetIds)

            datasetRows.map { row ->
                toDatasetResponse(
                    row,
                    completedItemCount = completedCounts[row[DatasetsTable.id]] ?: 0,
                    disputedItemCount = disputedCounts[row[DatasetsTable.id]] ?: 0,
                )
            }
        }

        return Result.Success(datasets)
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
    ): Result<ImportDataItemsResponse> {
        val items = request.items.map { item ->
            item.copy(
                content = item.content.trim(),
                contentType = item.contentType.trim().ifEmpty { "text" },
                metadata = item.metadata.trim().ifEmpty { "{}" },
            )
        }

        val validationError = validateDataItems(items)
        if (validationError != null) {
            return Result.BadRequest(validationError)
        }

        return try {
            val response = transaction {
                val dataset = datasetStore.findProviderDataset(providerId, datasetId)
                    ?: return@transaction null

                if (dataset.status != "draft") {
                    return@transaction ImportDataItemsTransactionResult.InvalidStatus
                }

                val now = OffsetDateTime.now()

                // 插入数据项
                dataItemStore.insertDataItems(datasetId, items, now)

                // 重新查询数据集的数据项总数并同步写回 datasets.item_count。
                // 使用实际查询值而非 items.size，确保与数据库状态一致（并发导入等场景）。
                val itemCount = dataItemStore.countDatasetItems(datasetId)
                datasetStore.updateDatasetItemCount(datasetId, itemCount, now)

                ImportDataItemsTransactionResult.Success(
                    ImportDataItemsResponse(
                        importedCount = items.size,
                        itemCount = itemCount,
                    )
                )
            }

            when (response) {
                null -> Result.BadRequest("数据集不存在或无权访问")
                ImportDataItemsTransactionResult.InvalidStatus -> Result.BadRequest("只能向草稿状态的数据集导入数据项")
                is ImportDataItemsTransactionResult.Success -> Result.Success(response.value)
            }
        } catch (_: ExposedSQLException) {
            Result.Conflict("数据项导入失败，请检查外部编号是否重复")
        }
    }

    /**
     * 查询指定提供者数据集下的数据项列表。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 查询结果，成功时返回按创建时间倒序排列的数据项列表
     */
    fun listProviderDataItems(providerId: UUID, datasetId: UUID): Result<List<DataItemResponse>> {
        val items = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction null

            // 查询指定数据集下的全部数据项，供提供者查看导入内容。
            dataItemStore.listDatasetItemRows(datasetId).map(::toDataItemResponse)
        }

        return if (items == null) {
            Result.BadRequest("数据集不存在或无权访问")
        } else {
            Result.Success(items)
        }
    }

    /**
     * 处理指定数据项的争议结果。
     *
     * 如果提供者最终结果与某条标注结果一致，则采纳对应标注；如果都不一致，
     * 则拒绝所有标注，并将提供者结果作为最终采纳结果保存到数据项。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param itemId 数据项 ID
     * @param request 争议处理请求
     * @return 处理结果
     */
    fun resolveDisputedDataItem(
        providerId: UUID,
        datasetId: UUID,
        itemId: UUID,
        request: ResolveDisputeRequest,
    ): Result<UpdateDatasetResponse> {
        val finalResult = request.finalResult.trim().ifEmpty { "{}" }
        val comment = request.comment?.trim()?.takeIf { it.isNotEmpty() }

        if (!isJsonObject(finalResult)) {
            return Result.BadRequest("最终标注结果必须是合法的 JSON 对象")
        }

        val result = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction ResolveDisputeTransactionResult.NotFound

            val item = DataItemsTable
                .selectAll()
                .where {
                    (DataItemsTable.id eq itemId) and
                        (DataItemsTable.datasetId eq datasetId)
                }
                .limit(1)
                .firstOrNull()
                ?: return@transaction ResolveDisputeTransactionResult.NotFound

            if (item[DataItemsTable.status] != "disputed") {
                return@transaction ResolveDisputeTransactionResult.InvalidStatus
            }

            val annotations = AnnotationsTable
                .selectAll()
                .where {
                    (AnnotationsTable.itemId eq itemId) and
                        (AnnotationsTable.roundNo eq item[DataItemsTable.currentRoundNo]) and
                        (AnnotationsTable.annotationType inList listOf("annotation", "review"))
                }
                .toList()

            val hasOriginal = annotations.any { it[AnnotationsTable.annotationType] == "annotation" }
            val hasReview = annotations.any { it[AnnotationsTable.annotationType] == "review" }
            if (!hasOriginal || !hasReview) {
                return@transaction ResolveDisputeTransactionResult.InvalidAnnotations
            }

            val now = OffsetDateTime.now()
            // 将提供者给出的最终结果与每条标注结果逐一比对，
            // 结果一致的标注被标记为采纳（accepted），不一致的标记为拒绝（rejected）。
            val matchedAnnotationIds = annotations
                .filter {
                    DatasetQueryHelper.areAnnotationResultsConsistent(
                        it[AnnotationsTable.result],
                        finalResult,
                    )
                }
                .map { it[AnnotationsTable.id] }
                .toSet()

            annotations.forEach { annotation ->
                val adopted = annotation[AnnotationsTable.id] in matchedAnnotationIds
                AnnotationsTable.update({ AnnotationsTable.id eq annotation[AnnotationsTable.id] }) {
                    it[AnnotationsTable.isDisputed] = false
                    it[status] = if (adopted) "accepted" else "rejected"
                    it[adoptionStatus] = if (adopted) 1.toShort() else 2.toShort()
                    it[adoptedAt] = now
                    it[adoptedBy] = providerId
                    it[adoptionComment] = comment
                    it[updatedAt] = now
                }
            }

            DataItemsTable.update({ DataItemsTable.id eq itemId }) {
                it[status] = "accepted"
                it[DataItemsTable.finalResult] = finalResult
                it[finalizedAt] = now
                it[finalizedBy] = providerId
                it[updatedAt] = now
            }

            DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
            DatasetQueryHelper.checkAndTransitionToReviewing(datasetId, now)
            ResolveDisputeTransactionResult.Success
        }

        return when (result) {
            ResolveDisputeTransactionResult.NotFound -> Result.BadRequest("数据项不存在或无权访问")
            ResolveDisputeTransactionResult.InvalidStatus -> Result.BadRequest("仅可处理争议状态的数据项")
            ResolveDisputeTransactionResult.InvalidAnnotations -> Result.BadRequest("争议数据项缺少原始标注或互查结果")
            ResolveDisputeTransactionResult.Success -> Result.Success(UpdateDatasetResponse("争议处理已保存"))
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
    ): Result<DeleteDataItemResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
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

            DeleteDataItemTransactionResult.Success(
                dataItemStore.countDatasetItems(datasetId).also { itemCount ->
                    datasetStore.updateDatasetItemCount(datasetId, itemCount, OffsetDateTime.now())
                }
            )
        }

        return when (result) {
            DeleteDataItemTransactionResult.NotFound -> Result.BadRequest("数据项不存在或无权访问")
            DeleteDataItemTransactionResult.InvalidStatus -> Result.BadRequest("只能删除草稿状态数据集的数据项")
            is DeleteDataItemTransactionResult.Success -> Result.Success(
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
    ): Result<UpdateDatasetResponse> {
        val name = request.name.trim()
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val annotationGuide = request.annotationGuide?.trim()?.takeIf { it.isNotEmpty() }
        val annotationSchema = request.annotationSchema.trim().ifEmpty { "{}" }
        val targetCompletionRatio = request.targetCompletionRatio.trim().ifEmpty { "50.00" }

        val validationError = validateDataset(name, annotationSchema, targetCompletionRatio)
        if (validationError != null) {
            return Result.BadRequest(validationError)
        }

        val ratio = targetCompletionRatio.toBigDecimal()

        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
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
            UpdateDatasetTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            UpdateDatasetTransactionResult.InvalidStatus -> Result.BadRequest("只能修改草稿状态的数据集")
            UpdateDatasetTransactionResult.Success -> Result.Success(UpdateDatasetResponse("数据集已更新"))
        }
    }

    /**
     * 发布指定提供者的草稿数据集，使其对标注员开放。
     *
     * 发布前会校验数据集是否包含至少一条数据项，且数据集当前必须为 `draft` 状态。
     * 发布后数据集状态变为 `in_progress`，标注员可以领取其中的任务。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 要发布的数据集 ID
     * @return 发布结果，成功时返回数据集的最新状态
     */
    fun publishProviderDataset(providerId: UUID, datasetId: UUID): Result<PublishDatasetResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction PublishDatasetTransactionResult.NotFound

            if (dataset.status != "draft") {
                return@transaction PublishDatasetTransactionResult.InvalidStatus
            }

            // 发布前校验数据集非空，避免标注员领取到空任务。
            val itemCount = DataItemsTable
                .selectAll()
                .where { DataItemsTable.datasetId eq datasetId }
                .count()
                .toInt()

            if (itemCount <= 0) {
                return@transaction PublishDatasetTransactionResult.EmptyDataset
            }

            DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                it[status] = "in_progress"
                it[DatasetsTable.itemCount] = itemCount
                it[updatedAt] = OffsetDateTime.now()
            }

            PublishDatasetTransactionResult.Success
        }

        return when (result) {
            PublishDatasetTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            PublishDatasetTransactionResult.InvalidStatus -> Result.BadRequest("只能发布草稿状态的数据集")
            PublishDatasetTransactionResult.EmptyDataset -> Result.BadRequest("发布前请先导入至少 1 条数据项")
            PublishDatasetTransactionResult.Success -> Result.Success(
                PublishDatasetResponse(
                    message = "数据集已发布",
                    status = "in_progress",
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
    fun deleteProviderDataset(providerId: UUID, datasetId: UUID): Result<DeleteDatasetResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
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
            DeleteDatasetTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            DeleteDatasetTransactionResult.InvalidStatus -> Result.BadRequest("只能删除草稿状态的数据集")
            DeleteDatasetTransactionResult.Success -> Result.Success(DeleteDatasetResponse("数据集已删除"))
        }
    }

    /**
     * 查询指定提供者数据集下所有争议状态的数据项。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 查询结果，成功时返回争议数据项列表
     */
    fun listDisputedDataItems(
        providerId: UUID,
        datasetId: UUID,
    ): Result<List<DataItemResponse>> {
        val items = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction null

            dataItemStore.listDisputedItemRows(datasetId).map(::toDataItemResponse)
        }

        return if (items == null) {
            Result.BadRequest("数据集不存在或无权访问")
        } else {
            Result.Success(items)
        }
    }

    /**
     * 查询指定争议数据项的详情，包含数据项内容、标注记录和数据集标注结构。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param itemId 数据项 ID
     * @return 查询结果，成功时返回争议详情
     */
    fun getDisputeDetail(
        providerId: UUID,
        datasetId: UUID,
        itemId: UUID,
    ): Result<DisputedItemDetailResponse> {
        val result = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction DisputeDetailResult.NotFound

            val item = dataItemStore.findDataItemRow(datasetId, itemId)
                ?: return@transaction DisputeDetailResult.NotFound

            if (item[DataItemsTable.status] != "disputed") {
                return@transaction DisputeDetailResult.InvalidStatus
            }

            val dataset = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction DisputeDetailResult.NotFound

            val annotations = annotationStore.listCurrentRoundAnnotationsForItem(itemId, item[DataItemsTable.currentRoundNo])

            // 批量查询标注员名称。
            val annotatorIds = annotations.map { it[AnnotationsTable.annotatorId] }.toSet()
            val annotatorNames = annotationStore.loadUserDisplayNames(annotatorIds)

            val annotationDetails = annotations.map { ann ->
                AnnotationDetailResponse(
                    id = ann[AnnotationsTable.id].toString(),
                    annotatorId = ann[AnnotationsTable.annotatorId].toString(),
                    annotatorName = annotatorNames[ann[AnnotationsTable.annotatorId].toString()] ?: "未知标注员",
                    annotationType = ann[AnnotationsTable.annotationType],
                    result = ann[AnnotationsTable.result],
                    comment = ann[AnnotationsTable.comment],
                    isDisputed = ann[AnnotationsTable.isDisputed],
                    status = ann[AnnotationsTable.status],
                    submittedAt = ann[AnnotationsTable.submittedAt].toString(),
                )
            }

            DisputeDetailResult.Success(
                DisputedItemDetailResponse(
                    item = toDataItemResponse(item),
                    annotations = annotationDetails,
                    annotationSchema = dataset[DatasetsTable.annotationSchema],
                    annotationGuide = dataset[DatasetsTable.annotationGuide],
                    datasetName = dataset[DatasetsTable.name],
                )
            )
        }

        return when (result) {
            DisputeDetailResult.NotFound -> Result.BadRequest("数据项不存在或无权访问")
            DisputeDetailResult.InvalidStatus -> Result.BadRequest("仅可查看争议状态的数据项")
            is DisputeDetailResult.Success -> Result.Success(result.value)
        }
    }


    /**
     * 查询指定数据集中所有已标注数据项及其标注记录，供提供者审核。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @return 审核详情，包含数据项列表及对应标注记录
     */
    fun listReviewItems(
        providerId: UUID,
        datasetId: UUID,
    ): Result<ReviewDetailResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction ReviewItemsResult.NotFound

            val ds = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction ReviewItemsResult.NotFound

            val currentStatus = ds[DatasetsTable.status]
            if (currentStatus != "reviewing") {
                val itemCount = ds[DatasetsTable.itemCount]
                if (itemCount > 0 && currentStatus == "in_progress") {
                    val completedCount = ds[DatasetsTable.completedItemCount]
                    val targetRatio = ds[DatasetsTable.targetCompletionRatio]
                    if (completedCount.toDouble() / itemCount.toDouble() * 100.0 >= targetRatio.toDouble()) {
                        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                            it[status] = "reviewing"
                            it[updatedAt] = OffsetDateTime.now()
                        }
                    } else {
                        return@transaction ReviewItemsResult.InvalidStatus
                    }
                } else {
                    return@transaction ReviewItemsResult.InvalidStatus
                }
            }

            val items = dataItemStore.listReviewableItemRowsForProvider(datasetId)

            val annotationsByItem = if (items.isEmpty()) {
                emptyMap()
            } else {
                val annRows = annotationStore.listCurrentRoundAnnotationsByItem(items)
                val flatAnnotations = annRows.values.flatten()
                val annotatorNames = annotationStore.loadUserDisplayNames(flatAnnotations.map { it[AnnotationsTable.annotatorId] }.toSet())
                annRows.mapValues { (_, anns) ->
                    anns.map { ann ->
                        AnnotationDetailResponse(
                            id = ann[AnnotationsTable.id].toString(),
                            annotatorId = ann[AnnotationsTable.annotatorId].toString(),
                            annotatorName = annotatorNames[ann[AnnotationsTable.annotatorId].toString()] ?: "未知标注员",
                            annotationType = ann[AnnotationsTable.annotationType],
                            result = ann[AnnotationsTable.result],
                            comment = ann[AnnotationsTable.comment],
                            isDisputed = ann[AnnotationsTable.isDisputed],
                            status = ann[AnnotationsTable.status],
                            submittedAt = ann[AnnotationsTable.submittedAt].toString(),
                        )
                    }
                }
            }

            val reviewedCount = DataItemsTable
                .selectAll()
                .where {
                    (DataItemsTable.datasetId eq datasetId) and
                        (DataItemsTable.status inList listOf("accepted", "rejected"))
                }
                .count()
                .toInt()

            val reviewItems = items.map { row ->
                ReviewItemResponse(
                    item = toDataItemResponse(row),
                    annotations = annotationsByItem[row[DataItemsTable.id]] ?: emptyList(),
                )
            }

            ReviewItemsResult.Success(
                ReviewDetailResponse(
                    datasetName = ds[DatasetsTable.name],
                    annotationSchema = ds[DatasetsTable.annotationSchema],
                    annotationGuide = ds[DatasetsTable.annotationGuide],
                    items = reviewItems,
                    reviewedItemCount = reviewedCount,
                    totalItemCount = ds[DatasetsTable.itemCount],
                )
            )
        }

        return when (result) {
            ReviewItemsResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            ReviewItemsResult.InvalidStatus -> Result.BadRequest("仅可审核状态为「审核中」的数据集")
            is ReviewItemsResult.Success -> Result.Success(result.value)
        }
    }

    /**
     * 提交数据集审核结果。
     *
     * 提供者审核数据集标注质量后，可提交阶段性审核结论。
     * 数据集保持在 `reviewing` 状态，直到手动标记完成。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param request 审核请求
     * @return 提交结果
     */
    fun submitReview(
        providerId: UUID,
        datasetId: UUID,
        request: SubmitReviewRequest,
    ): Result<SubmitReviewResponse> {
        val reviewStatus = request.status.trim().lowercase()
        val opinion = request.opinion?.trim()?.takeIf { it.isNotEmpty() }
        val validStatuses = setOf("approved", "revision_required")

        if (reviewStatus !in validStatuses) {
            return Result.BadRequest("审核结果必须为 approved 或 revision_required")
        }

        val result = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction SubmitReviewTransactionResult.NotFound

            val ds = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction SubmitReviewTransactionResult.NotFound

            if (ds[DatasetsTable.status] != "reviewing") {
                return@transaction SubmitReviewTransactionResult.InvalidStatus
            }

            val now = OffsetDateTime.now()
            val sampledCount = request.sampledItemCount
                ?: DataItemsTable
                    .selectAll()
                    .where {
                        (DataItemsTable.datasetId eq datasetId) and
                            (DataItemsTable.status inList listOf("annotated", "disputed", "accepted"))
                    }
                    .count()
                    .toInt()

            val disputedCount = DataItemsTable
                .selectAll()
                .where {
                    (DataItemsTable.datasetId eq datasetId) and
                        (DataItemsTable.status eq "disputed")
                }
                .count()
                .toInt()

            // 创建或更新审核记录
            val existingReview = DatasetReviewsTable
                .selectAll()
                .where {
                    (DatasetReviewsTable.datasetId eq datasetId) and
                        (DatasetReviewsTable.providerId eq providerId)
                }
                .limit(1)
                .firstOrNull()

            if (existingReview != null) {
                DatasetReviewsTable.update({ DatasetReviewsTable.id eq existingReview[DatasetReviewsTable.id] }) {
                    it[DatasetReviewsTable.status] = reviewStatus
                    it[DatasetReviewsTable.sampledItemCount] = sampledCount
                    it[DatasetReviewsTable.disputedItemCount] = disputedCount
                    it[DatasetReviewsTable.opinion] = opinion
                    it[DatasetReviewsTable.reviewedAt] = now
                    it[updatedAt] = now
                }
            } else {
                DatasetReviewsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[DatasetReviewsTable.datasetId] = datasetId
                    it[DatasetReviewsTable.providerId] = providerId
                    it[DatasetReviewsTable.status] = reviewStatus
                    it[DatasetReviewsTable.sampledItemCount] = sampledCount
                    it[DatasetReviewsTable.disputedItemCount] = disputedCount
                    it[DatasetReviewsTable.opinion] = opinion
                    it[DatasetReviewsTable.reviewedAt] = now
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                it[DatasetsTable.status] = "reviewing"
                it[updatedAt] = now
            }

            SubmitReviewTransactionResult.Success("reviewing")
        }

        return when (result) {
            SubmitReviewTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            SubmitReviewTransactionResult.InvalidStatus -> Result.BadRequest("仅可审核状态为「审核中」的数据集")
            is SubmitReviewTransactionResult.Success -> Result.Success(
                SubmitReviewResponse(
                    message = "审核结果已提交，数据集保持审核中",
                    datasetStatus = result.datasetStatus,
                )
            )
        }
    }

    /**
     * 逐条审核数据项，标记为通过或不通过。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param itemId 数据项 ID
     * @param accepted 是否通过审核
     * @return 操作结果
     */
    fun reviewItem(
        providerId: UUID,
        datasetId: UUID,
        itemId: UUID,
        accepted: Boolean,
    ): Result<UpdateDatasetResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction ReviewItemTransactionResult.NotFound

            if (!DatasetStatusPolicy.canReviewDataset(dataset.status)) {
                return@transaction ReviewItemTransactionResult.InvalidStatus
            }

            val item = dataItemStore.findDataItemRow(datasetId, itemId)
                ?: return@transaction ReviewItemTransactionResult.NotFound

            if (!DatasetStatusPolicy.canReviewItem(item[DataItemsTable.status])) {
                return@transaction ReviewItemTransactionResult.InvalidStatus
            }

            val now = OffsetDateTime.now()
            dataItemStore.updateDataItemReviewStatus(itemId, accepted, now)

            ReviewItemTransactionResult.Success
        }

        return when (result) {
            ReviewItemTransactionResult.NotFound -> Result.BadRequest("数据项不存在或无权访问")
            ReviewItemTransactionResult.InvalidStatus -> Result.BadRequest("该数据项尚无法审核")
            ReviewItemTransactionResult.Success -> Result.Success(UpdateDatasetResponse("审核结果已保存"))
        }
    }

    /**
     * 保存数据集审核结果，但保持数据集停留在 `reviewing` 状态。
     *
     * @param providerId 数据集提供者用户 ID
     * @param datasetId 数据集 ID
     * @param request 完成审核请求（含可选意见）
     * @return 操作结果
     */
    fun finishReview(
        providerId: UUID,
        datasetId: UUID,
        request: FinishReviewRequest,
    ): Result<FinishReviewResponse> {
        val result = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction FinishReviewTransactionResult.NotFound

            val ds = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction FinishReviewTransactionResult.NotFound

            if (!DatasetStatusPolicy.canReviewDataset(ds[DatasetsTable.status])) {
                return@transaction FinishReviewTransactionResult.InvalidStatus
            }

            val now = OffsetDateTime.now()
            val opinion = request.opinion?.trim()?.takeIf { it.isNotEmpty() }

            val acceptedCount = dataItemStore.countDatasetItemsByStatuses(datasetId, listOf("accepted"))
            val rejectedCount = dataItemStore.countDatasetItemsByStatuses(datasetId, listOf("rejected"))
            val totalReviewable = dataItemStore.countDatasetItemsByStatuses(datasetId, listOf("annotated", "accepted", "rejected"))

            val reviewedCount = acceptedCount + rejectedCount
            if (reviewedCount < totalReviewable) {
                return@transaction FinishReviewTransactionResult.NotAllReviewed
            }

            val reviewStatus = if (rejectedCount > 0) "revision_required" else "approved"

            val sampledCount = acceptedCount + rejectedCount
            datasetReviewStore.upsertDatasetReview(datasetId, providerId, reviewStatus, sampledCount, 0, opinion, now)
            datasetStore.updateDatasetStatus(datasetId, "reviewing", now)

            FinishReviewTransactionResult.Success("reviewing", acceptedCount, rejectedCount)
        }

        return when (result) {
            FinishReviewTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            FinishReviewTransactionResult.InvalidStatus -> Result.BadRequest("仅可完成审核状态为「审核中」的数据集")
            FinishReviewTransactionResult.NotAllReviewed -> Result.BadRequest("尚有数据项未完成逐条审核")
            is FinishReviewTransactionResult.Success -> Result.Success(
                FinishReviewResponse(
                    message = "审核结果已保存",
                    datasetStatus = result.datasetStatus,
                    acceptedCount = result.acceptedCount,
                    rejectedCount = result.rejectedCount,
                )
            )
        }
    }

    /**
     * 重新发布审核未通过的数据项，开启新一轮标注。
     *
     * 数据集保持在 `reviewing` 状态，仅将未通过的数据项推进到下一轮并重新置为待标注。
     */
    fun republishRejectedItems(
        providerId: UUID,
        datasetId: UUID,
    ): Result<UpdateDatasetResponse> {
        val result = transaction {
            val dataset = datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction RepublishRejectedItemsResult.NotFound

            if (!DatasetStatusPolicy.canRepublishDataset(dataset.status)) {
                return@transaction RepublishRejectedItemsResult.InvalidStatus
            }

            val rejectedItems = dataItemStore.listRejectedItemRows(datasetId)

            if (rejectedItems.isEmpty()) {
                return@transaction RepublishRejectedItemsResult.NoRejectedItems
            }

            val now = OffsetDateTime.now()
            dataItemStore.republishRejectedItems(rejectedItems, now)

            DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
            datasetStore.updateDatasetStatus(datasetId, "reviewing", now)

            RepublishRejectedItemsResult.Success(rejectedItems.size)
        }

        return when (result) {
            RepublishRejectedItemsResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            RepublishRejectedItemsResult.InvalidStatus -> Result.BadRequest("仅可重新发布审核中的数据集")
            RepublishRejectedItemsResult.NoRejectedItems -> Result.BadRequest("当前没有可重新发布的未通过数据项")
            is RepublishRejectedItemsResult.Success -> Result.Success(
                UpdateDatasetResponse("已重新发布 ${result.count} 条未通过数据项")
            )
        }
    }

    /**
     * 手动将审核中的数据集标记为完成。
     *
     * 仅当数据集下所有数据项都已审核通过时允许完成。
     */
    fun completeReview(
        providerId: UUID,
        datasetId: UUID,
    ): Result<UpdateDatasetResponse> {
        val result = transaction {
            datasetStore.findProviderDataset(providerId, datasetId)
                ?: return@transaction CompleteReviewTransactionResult.NotFound

            val dataset = datasetStore.findDatasetRow(datasetId)
                ?: return@transaction CompleteReviewTransactionResult.NotFound

            if (!DatasetStatusPolicy.canCompleteReview(dataset[DatasetsTable.status])) {
                return@transaction CompleteReviewTransactionResult.InvalidStatus
            }

            val hasUnfinishedItems = dataItemStore.hasNonAcceptedItems(datasetId)

            if (hasUnfinishedItems) {
                return@transaction CompleteReviewTransactionResult.HasUnfinishedItems
            }

            val now = OffsetDateTime.now()
            datasetStore.updateDatasetStatus(datasetId, "completed", now)
            val existingReview = datasetReviewStore.findDatasetReviewRow(datasetId, providerId)
            if (existingReview != null) {
                datasetReviewStore.updateDatasetReviewStatus(existingReview[DatasetReviewsTable.id], "approved", now)
            }

            CompleteReviewTransactionResult.Success
        }

        return when (result) {
            CompleteReviewTransactionResult.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            CompleteReviewTransactionResult.InvalidStatus -> Result.BadRequest("仅可完成审核中的数据集")
            CompleteReviewTransactionResult.HasUnfinishedItems -> Result.BadRequest("仍有未通过或未处理的数据项，无法标记完成")
            CompleteReviewTransactionResult.Success -> Result.Success(UpdateDatasetResponse("数据集已标记完成"))
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
    private fun validateDataItems(items: List<com.annodata.api.models.DataItemInput>): String? {
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
     * 判断输入字符串是否为 JSON 对象。
     *
     * @param value 待校验的 JSON 字符串
     * @return 是 JSON 对象时返回 true
     */
    private fun isJsonObject(value: String): Boolean {
        return runCatching { objectMapper.readTree(value)?.isObject == true }.getOrDefault(false)
    }

}

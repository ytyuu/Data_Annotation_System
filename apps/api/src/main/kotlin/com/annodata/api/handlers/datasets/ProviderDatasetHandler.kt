package com.annodata.api.handlers.datasets

import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.unauthorized
import com.annodata.api.middleware.auth.currentUser
import com.annodata.api.models.CreateDatasetRequest
import com.annodata.api.models.ImportDataItemsRequest
import com.annodata.api.models.ResolveDisputeRequest
import com.annodata.api.models.SubmitReviewRequest
import com.annodata.api.models.ReviewItemActionRequest
import com.annodata.api.models.FinishReviewRequest
import com.annodata.api.models.UpdateDatasetRequest
import com.annodata.api.http.Result
import com.annodata.api.service.dataset.ProviderDatasetService
import io.javalin.http.Context
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.UUID

/**
 * 数据集提供者的数据集请求处理器。
 *
 * @property datasetService 数据集提供者业务服务
 */
class ProviderDatasetHandler(private val datasetService: ProviderDatasetService) {
    /**
     * 处理 `POST /api/provider/datasets` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun create(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val request = ctx.bodyAsClass(CreateDatasetRequest::class.java)

        when (val result = datasetService.createProviderDataset(providerId, request)) {
            is Result.Success -> ctx.status(201).json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `PUT /api/provider/datasets/{datasetId}` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun update(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val request = ctx.bodyAsClass(UpdateDatasetRequest::class.java)

        when (val result = datasetService.updateProviderDataset(providerId, datasetId, request)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/provider/datasets` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun list(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)

        when (val result = datasetService.listProviderDatasets(providerId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/items` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun importItems(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val request = ctx.bodyAsClass(ImportDataItemsRequest::class.java)

        when (val result = datasetService.importDataItems(providerId, datasetId, request)) {
            is Result.Success -> ctx.status(201).json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/items/import-csv` 请求。
     */
    fun importItemsFromCsv(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val file = ctx.uploadedFile("file")

        if (file == null) {
            ctx.badRequest("请选择要导入的 CSV 文件")
            return
        }
        val filename = file.filename().substringAfterLast('/').substringAfterLast('\\')
        if (!filename.lowercase().endsWith(".csv")) {
            ctx.badRequest("仅支持上传 .csv 文件")
            return
        }
        if (file.size() == 0L) {
            ctx.badRequest("CSV 文件不能为空")
            return
        }

        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        InputStreamReader(file.content(), utf8Decoder).buffered().use { reader ->
            when (
                val result = datasetService.importDataItemsFromCsv(
                    providerId = providerId,
                    datasetId = datasetId,
                    filename = filename,
                    reader = reader,
                )
            ) {
                is Result.Success -> ctx.status(201).json(result.value)
                is Result.BadRequest -> ctx.badRequest(result.message)
                is Result.Unauthorized -> ctx.unauthorized(result.message)
                is Result.Forbidden -> ctx.forbidden(result.message)
                is Result.Conflict -> ctx.conflict(result.message)
            }
        }
    }

    /**
     * 处理 `GET /api/provider/datasets/{datasetId}/items` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listItems(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.listProviderDataItems(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `DELETE /api/provider/datasets/{datasetId}/items/{itemId}` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun deleteItem(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val itemId = UUID.fromString(ctx.pathParam("itemId"))

        when (val result = datasetService.deleteProviderDataItem(providerId, datasetId, itemId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/items/{itemId}/resolve-dispute` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun resolveDispute(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val itemId = UUID.fromString(ctx.pathParam("itemId"))
        val request = ctx.bodyAsClass(ResolveDisputeRequest::class.java)

        when (val result = datasetService.resolveDisputedDataItem(providerId, datasetId, itemId, request)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/publish` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun publish(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.publishProviderDataset(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/provider/datasets/{datasetId}/disputed-items` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listDisputedItems(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.listDisputedDataItems(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/provider/datasets/{datasetId}/items/{itemId}/dispute-detail` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun getDisputeDetail(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val itemId = UUID.fromString(ctx.pathParam("itemId"))

        when (val result = datasetService.getDisputeDetail(providerId, datasetId, itemId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/provider/datasets/{datasetId}/review-items` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listReviewItems(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.listReviewItems(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/submit-review` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun submitReview(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val request = ctx.bodyAsClass(SubmitReviewRequest::class.java)

        when (val result = datasetService.submitReview(providerId, datasetId, request)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/review-item/{itemId}` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun reviewItem(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val itemId = UUID.fromString(ctx.pathParam("itemId"))
        val request = ctx.bodyAsClass(ReviewItemActionRequest::class.java)

        when (val result = datasetService.reviewItem(providerId, datasetId, itemId, request.accepted)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/finish-review` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun finishReview(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val request = ctx.bodyAsClass(FinishReviewRequest::class.java)

        when (val result = datasetService.finishReview(providerId, datasetId, request)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/complete-review` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun completeReview(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.completeReview(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
        }
    }

    /**
     * 处理 `POST /api/provider/datasets/{datasetId}/republish` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun republish(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.republishRejectedItems(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
        }
    }

    /**
     * 处理 `DELETE /api/provider/datasets/{datasetId}` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun delete(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.deleteProviderDataset(providerId, datasetId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }
}

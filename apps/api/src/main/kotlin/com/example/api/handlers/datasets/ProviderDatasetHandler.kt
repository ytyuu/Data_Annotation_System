package com.example.api.handlers.datasets

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.middleware.auth.currentUser
import com.example.api.models.CreateDatasetRequest
import com.example.api.models.ImportDataItemsRequest
import com.example.api.models.ResolveDisputeRequest
import com.example.api.models.UpdateDatasetRequest
import com.example.api.service.auth.AuthResult
import com.example.api.service.dataset.ProviderDatasetService
import io.javalin.http.Context
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
            is AuthResult.Success -> ctx.status(201).json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.status(201).json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
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
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }
}

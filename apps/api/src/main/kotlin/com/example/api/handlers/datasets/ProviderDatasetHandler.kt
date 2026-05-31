package com.example.api.handlers.datasets

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.middleware.auth.currentUser
import com.example.api.models.CreateDatasetRequest
import com.example.api.service.auth.AuthResult
import com.example.api.service.dataset.DatasetService
import io.javalin.http.Context
import java.util.UUID

/**
 * 数据集提供者的数据集请求处理器。
 *
 * @property datasetService 数据集业务服务
 */
class ProviderDatasetHandler(private val datasetService: DatasetService) {
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
}

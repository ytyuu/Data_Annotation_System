package com.example.api.handlers.datasets

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.middleware.auth.currentUser
import com.example.api.models.ClaimTasksRequest
import com.example.api.service.auth.AuthResult
import com.example.api.service.dataset.DatasetService
import io.javalin.http.Context
import java.util.UUID

/**
 * 数据标注员可访问的数据集请求处理器。
 *
 * @property datasetService 数据集业务服务
 */
class AnnotatorDatasetHandler(private val datasetService: DatasetService) {
    /**
     * 处理 `GET /api/annotator/datasets` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listOpen(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)

        when (val result = datasetService.listOpenDatasets(annotatorId)) {
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/annotator/datasets/{datasetId}/claim` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun claim(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))
        val request = ctx.bodyAsClass(ClaimTasksRequest::class.java)

        when (val result = datasetService.claimAnnotatorTasks(annotatorId, datasetId, request.count)) {
            is AuthResult.Success -> ctx.status(201).json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/annotator/tasks` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listTasks(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val statusFilter = ctx.queryParam("status")

        when (val result = datasetService.listAnnotatorTasks(annotatorId, statusFilter)) {
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/annotator/datasets/{datasetId}/tasks` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listDatasetTasks(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val datasetId = UUID.fromString(ctx.pathParam("datasetId"))

        when (val result = datasetService.listDatasetTasks(annotatorId, datasetId)) {
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }
}

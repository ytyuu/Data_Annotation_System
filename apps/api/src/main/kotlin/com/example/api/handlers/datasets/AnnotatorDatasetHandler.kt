package com.example.api.handlers.datasets

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.middleware.auth.currentUser
import com.example.api.models.ClaimTasksRequest
import com.example.api.models.SubmitAnnotationBatchRequest
import com.example.api.http.Result
import com.example.api.service.dataset.AnnotatorDatasetService
import io.javalin.http.Context
import java.util.UUID

/**
 * 数据标注员可访问的数据集请求处理器。
 *
 * @property datasetService 数据标注员业务服务
 */
class AnnotatorDatasetHandler(private val datasetService: AnnotatorDatasetService) {
    /**
     * 处理 `GET /api/annotator/datasets` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listOpen(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)

        when (val result = datasetService.listOpenDatasets(annotatorId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
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

        when (val result = datasetService.claimAnnotatorTasks(annotatorId, datasetId, request.count, request.taskType)) {
            is Result.Success -> ctx.status(201).json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
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
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/annotator/task-batches/{batchId}/tasks` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listBatchTasks(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val batchId = UUID.fromString(ctx.pathParam("batchId"))

        when (val result = datasetService.listBatchTasks(annotatorId, batchId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `GET /api/annotator/task-batches/{batchId}/workspace` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun getBatchWorkspace(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val batchId = UUID.fromString(ctx.pathParam("batchId"))

        when (val result = datasetService.getAnnotatorTaskWorkspace(annotatorId, batchId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/annotator/task-batches/{batchId}/return` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun returnTaskBatch(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val batchId = UUID.fromString(ctx.pathParam("batchId"))

        when (val result = datasetService.returnTaskBatch(annotatorId, batchId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/annotator/task-batches/{batchId}/submit` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun submitBatch(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val batchId = UUID.fromString(ctx.pathParam("batchId"))
        val request = ctx.bodyAsClass(SubmitAnnotationBatchRequest::class.java)

        when (val result = datasetService.submitAnnotationBatch(annotatorId, batchId, request.submissions)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    /**
     * 处理 `POST /api/annotator/task-batches/{batchId}/start` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun startTaskBatch(ctx: Context) {
        val annotatorId = UUID.fromString(ctx.currentUser().id)
        val batchId = UUID.fromString(ctx.pathParam("batchId"))

        when (val result = datasetService.startTaskBatch(annotatorId, batchId)) {
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }
}

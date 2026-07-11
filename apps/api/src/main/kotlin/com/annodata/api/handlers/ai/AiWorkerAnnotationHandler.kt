package com.annodata.api.handlers.ai

import com.annodata.api.http.Result
import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.unauthorized
import com.annodata.api.models.FailAiAnnotationBatchRequest
import com.annodata.api.models.SubmitAiAnnotationResultsRequest
import com.annodata.api.service.ai.AiWorkerService
import io.javalin.http.Context
import java.util.UUID

class AiWorkerAnnotationHandler(private val service: AiWorkerService) {
    fun claimItems(ctx: Context) {
        val batchId = parseBatchId(ctx) ?: return
        val limit = ctx.queryParam("limit")?.toIntOrNull() ?: 100
        respond(ctx, service.claimItems(batchId, limit))
    }

    fun submitResults(ctx: Context) {
        val batchId = parseBatchId(ctx) ?: return
        val request = ctx.bodyAsClass(SubmitAiAnnotationResultsRequest::class.java)
        respond(ctx, service.submitResults(batchId, request))
    }

    fun failBatch(ctx: Context) {
        val batchId = parseBatchId(ctx) ?: return
        val request = ctx.bodyAsClass(FailAiAnnotationBatchRequest::class.java)
        respond(ctx, service.failBatch(batchId, request))
    }

    private fun <T> respond(ctx: Context, result: Result<T>) {
        when (result) {
            is Result.Success -> ctx.json(result.value as Any)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    private fun parseBatchId(ctx: Context): UUID? {
        return runCatching { UUID.fromString(ctx.pathParam("batchId")) }.getOrNull()
            ?: run {
                ctx.badRequest("batchId 无效")
                null
            }
    }
}

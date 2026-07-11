package com.annodata.api.handlers.ai

import com.annodata.api.http.Result
import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.unauthorized
import com.annodata.api.middleware.auth.currentUser
import com.annodata.api.models.CreateAiAnnotationBatchRequest
import com.annodata.api.models.BatchReviewAiAnnotationResultsRequest
import com.annodata.api.models.ReviewAiAnnotationResultRequest
import com.annodata.api.service.ai.AiAnnotationReviewService
import com.annodata.api.service.ai.AiAnnotationService
import io.javalin.http.Context
import java.util.UUID

class AiAnnotationHandler(private val service: AiAnnotationService) {
    private val reviewService = AiAnnotationReviewService()
    fun createBatch(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = parseUuid(ctx, ctx.pathParam("datasetId"), "datasetId") ?: return
        val request = ctx.bodyAsClass(CreateAiAnnotationBatchRequest::class.java)
        respond(ctx, service.createBatch(providerId, datasetId, request), successStatus = 201)
    }

    fun listBatches(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val datasetId = parseUuid(ctx, ctx.pathParam("datasetId"), "datasetId") ?: return
        respond(ctx, service.listBatches(providerId, datasetId))
    }

    fun getBatch(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val batchId = parseUuid(ctx, ctx.pathParam("batchId"), "batchId") ?: return
        respond(ctx, service.getBatch(providerId, batchId))
    }

    fun listResults(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val batchId = parseUuid(ctx, ctx.queryParam("batchId"), "batchId") ?: return
        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val pageSize = ctx.queryParam("pageSize")?.toIntOrNull() ?: 50
        respond(
            ctx,
            reviewService.listResults(
                providerId,
                batchId,
                ctx.queryParam("status"),
                ctx.queryParam("reviewMode"),
                page,
                pageSize,
            )
        )
    }

    fun reviewResult(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val resultId = parseUuid(ctx, ctx.pathParam("resultId"), "resultId") ?: return
        val request = ctx.bodyAsClass(ReviewAiAnnotationResultRequest::class.java)
        respond(ctx, reviewService.reviewResult(providerId, resultId, request))
    }

    fun batchReview(ctx: Context) {
        val providerId = UUID.fromString(ctx.currentUser().id)
        val request = ctx.bodyAsClass(BatchReviewAiAnnotationResultsRequest::class.java)
        respond(ctx, reviewService.batchReview(providerId, request))
    }

    private fun <T> respond(ctx: Context, result: Result<T>, successStatus: Int = 200) {
        when (result) {
            is Result.Success -> ctx.status(successStatus).json(result.value as Any)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }

    private fun parseUuid(ctx: Context, raw: String?, fieldName: String): UUID? {
        return runCatching { UUID.fromString(raw) }.getOrNull()
            ?: run {
                ctx.badRequest("$fieldName 无效")
                null
            }
    }
}

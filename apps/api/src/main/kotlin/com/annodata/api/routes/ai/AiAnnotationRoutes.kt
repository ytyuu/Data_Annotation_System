package com.annodata.api.routes.ai

import com.annodata.api.handlers.ai.AiAnnotationHandler
import com.annodata.api.handlers.ai.AiWorkerAnnotationHandler
import com.annodata.api.middleware.ai.AiWorkerAuthMiddleware
import com.annodata.api.middleware.ai.requireAiWorker
import com.annodata.api.middleware.auth.AuthMiddleware
import com.annodata.api.middleware.auth.requireAuth
import com.annodata.api.middleware.auth.requireRole
import com.annodata.api.routes.routeGroup
import com.annodata.api.service.ai.AiAnnotationService
import com.annodata.api.service.ai.AiWorkerDispatcher
import com.annodata.api.service.ai.AiWorkerService

fun registerAiAnnotationRoutes(
    authMiddleware: AuthMiddleware?,
    aiWorkerAuthMiddleware: AiWorkerAuthMiddleware?,
    aiWorkerDispatcher: AiWorkerDispatcher?,
) {
    val handler = AiAnnotationHandler(AiAnnotationService(aiWorkerDispatcher))
    val workerHandler = AiWorkerAnnotationHandler(AiWorkerService())

    routeGroup(requireAuth(authMiddleware), requireRole("provider")) {
        post("/provider/datasets/{datasetId}/ai-annotation-batches") { ctx -> handler.createBatch(ctx) }
        get("/provider/datasets/{datasetId}/ai-annotation-batches") { ctx -> handler.listBatches(ctx) }
        get("/provider/ai-annotation-batches/{batchId}") { ctx -> handler.getBatch(ctx) }
        post("/provider/ai-annotation-batches/{batchId}/run") { ctx -> handler.runBatch(ctx) }
        delete("/provider/ai-annotation-batches/{batchId}") { ctx -> handler.deleteBatch(ctx) }
        get("/provider/ai-annotation-results") { ctx -> handler.listResults(ctx) }
        post("/provider/ai-annotation-results/{resultId}/review") { ctx -> handler.reviewResult(ctx) }
        post("/provider/ai-annotation-results/batch-review") { ctx -> handler.batchReview(ctx) }
    }

    routeGroup(requireAiWorker(aiWorkerAuthMiddleware)) {
        get("/ai/annotation-batches/{batchId}/items") { ctx -> workerHandler.claimItems(ctx) }
        post("/ai/annotation-batches/{batchId}/results") { ctx -> workerHandler.submitResults(ctx) }
        post("/ai/annotation-batches/{batchId}/fail") { ctx -> workerHandler.failBatch(ctx) }
    }
}

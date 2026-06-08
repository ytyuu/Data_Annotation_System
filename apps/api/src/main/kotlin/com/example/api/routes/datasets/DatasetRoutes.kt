package com.example.api.routes.datasets

import com.example.api.handlers.datasets.AnnotatorDatasetHandler
import com.example.api.handlers.datasets.ProviderDatasetHandler
import com.example.api.middleware.auth.AuthMiddleware
import com.example.api.middleware.auth.requireAuth
import com.example.api.middleware.auth.requireRole
import com.example.api.routes.routeGroup
import com.example.api.service.dataset.AnnotatorDatasetService
import com.example.api.service.dataset.ProviderDatasetService

/**
 * 注册数据集相关路由。
 *
 * @param authMiddleware 认证中间件，为 null 时受保护路由返回 500
 */
fun registerDatasetRoutes(authMiddleware: AuthMiddleware?) {
    val providerDatasetHandler = ProviderDatasetHandler(ProviderDatasetService())
    val annotatorDatasetHandler = AnnotatorDatasetHandler(AnnotatorDatasetService())

    routeGroup(requireAuth(authMiddleware), requireRole("provider")) {
        get("/provider/datasets") { ctx -> providerDatasetHandler.list(ctx) }
        post("/provider/datasets") { ctx -> providerDatasetHandler.create(ctx) }
        put("/provider/datasets/{datasetId}") { ctx -> providerDatasetHandler.update(ctx) }
        get("/provider/datasets/{datasetId}/items") { ctx -> providerDatasetHandler.listItems(ctx) }
        post("/provider/datasets/{datasetId}/items") { ctx -> providerDatasetHandler.importItems(ctx) }
        get("/provider/datasets/{datasetId}/disputed-items") { ctx -> providerDatasetHandler.listDisputedItems(ctx) }
        get("/provider/datasets/{datasetId}/items/{itemId}/dispute-detail") { ctx ->
            providerDatasetHandler.getDisputeDetail(ctx)
        }
        post("/provider/datasets/{datasetId}/items/{itemId}/resolve-dispute") { ctx ->
            providerDatasetHandler.resolveDispute(ctx)
        }
        get("/provider/datasets/{datasetId}/review-items") { ctx -> providerDatasetHandler.listReviewItems(ctx) }
        post("/provider/datasets/{datasetId}/review-item/{itemId}") { ctx -> providerDatasetHandler.reviewItem(ctx) }
        post("/provider/datasets/{datasetId}/finish-review") { ctx -> providerDatasetHandler.finishReview(ctx) }
        post("/provider/datasets/{datasetId}/complete-review") { ctx -> providerDatasetHandler.completeReview(ctx) }
        post("/provider/datasets/{datasetId}/republish") { ctx -> providerDatasetHandler.republish(ctx) }
        post("/provider/datasets/{datasetId}/submit-review") { ctx -> providerDatasetHandler.submitReview(ctx) }
        delete("/provider/datasets/{datasetId}/items/{itemId}") { ctx -> providerDatasetHandler.deleteItem(ctx) }
        post("/provider/datasets/{datasetId}/publish") { ctx -> providerDatasetHandler.publish(ctx) }
        delete("/provider/datasets/{datasetId}") { ctx -> providerDatasetHandler.delete(ctx) }
    }

    routeGroup(requireAuth(authMiddleware), requireRole("annotator")) {
        get("/annotator/datasets") { ctx -> annotatorDatasetHandler.listOpen(ctx) }
        post("/annotator/datasets/{datasetId}/claim") { ctx -> annotatorDatasetHandler.claim(ctx) }
        get("/annotator/tasks") { ctx -> annotatorDatasetHandler.listTasks(ctx) }
        get("/annotator/task-batches/{batchId}/tasks") { ctx -> annotatorDatasetHandler.listBatchTasks(ctx) }
        get("/annotator/task-batches/{batchId}/workspace") { ctx -> annotatorDatasetHandler.getBatchWorkspace(ctx) }
        post("/annotator/task-batches/{batchId}/return") { ctx -> annotatorDatasetHandler.returnTaskBatch(ctx) }
        post("/annotator/task-batches/{batchId}/submit") { ctx -> annotatorDatasetHandler.submitBatch(ctx) }
        post("/annotator/task-batches/{batchId}/start") { ctx -> annotatorDatasetHandler.startTaskBatch(ctx) }
    }
}

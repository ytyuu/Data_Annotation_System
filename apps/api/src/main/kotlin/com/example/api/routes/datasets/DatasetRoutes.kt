package com.example.api.routes.datasets

import com.example.api.handlers.datasets.AnnotatorDatasetHandler
import com.example.api.handlers.datasets.ProviderDatasetHandler
import com.example.api.middleware.auth.AuthMiddleware
import com.example.api.middleware.auth.requireAuth
import com.example.api.middleware.auth.requireRole
import com.example.api.routes.routeGroup
import com.example.api.service.dataset.DatasetService

/**
 * 注册数据集相关路由。
 *
 * 当前仅包含数据集提供者可访问的创建和列表接口。
 *
 * @param authMiddleware 认证中间件，为 null 时受保护路由返回 500
 */
fun registerDatasetRoutes(authMiddleware: AuthMiddleware?) {
    val providerDatasetHandler = ProviderDatasetHandler(DatasetService())
    val annotatorDatasetHandler = AnnotatorDatasetHandler(DatasetService())

    routeGroup(requireAuth(authMiddleware), requireRole("provider")) {
        get("/provider/datasets") { ctx -> providerDatasetHandler.list(ctx) }
        post("/provider/datasets") { ctx -> providerDatasetHandler.create(ctx) }
        put("/provider/datasets/{datasetId}") { ctx -> providerDatasetHandler.update(ctx) }
        get("/provider/datasets/{datasetId}/items") { ctx -> providerDatasetHandler.listItems(ctx) }
        post("/provider/datasets/{datasetId}/items") { ctx -> providerDatasetHandler.importItems(ctx) }
        delete("/provider/datasets/{datasetId}/items/{itemId}") { ctx -> providerDatasetHandler.deleteItem(ctx) }
        post("/provider/datasets/{datasetId}/publish") { ctx -> providerDatasetHandler.publish(ctx) }
        delete("/provider/datasets/{datasetId}") { ctx -> providerDatasetHandler.delete(ctx) }
    }

    routeGroup(requireAuth(authMiddleware), requireRole("annotator")) {
        get("/annotator/datasets") { ctx -> annotatorDatasetHandler.listOpen(ctx) }
    }
}

/**
 * 健康检查路由注册。
 */
package com.example.api.routes.health

import com.example.api.handlers.health.DatabaseHealthHandler
import com.example.api.handlers.health.HealthHandler
import io.javalin.apibuilder.ApiBuilder.get

/**
 * 注册健康检查相关路由（/health、/health/database）。
 */
fun registerHealthRoutes() {
    get("/health") { ctx -> HealthHandler.show(ctx) }
    get("/health/database") { ctx -> DatabaseHealthHandler.show(ctx) }
}

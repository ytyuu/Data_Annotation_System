/**
 * 健康检查路由注册。
 */
package com.annodata.api.routes.health

import com.annodata.api.handlers.health.DatabaseHealthHandler
import com.annodata.api.handlers.health.HealthHandler
import io.javalin.apibuilder.ApiBuilder.get

/**
 * 注册健康检查相关路由（/health、/health/database）。
 */
fun registerHealthRoutes() {
    get("/health") { ctx -> HealthHandler.show(ctx) }
    get("/health/database") { ctx -> DatabaseHealthHandler.show(ctx) }
}

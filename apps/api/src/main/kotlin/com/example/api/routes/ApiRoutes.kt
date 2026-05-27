package com.example.api.routes

import com.example.api.config.ServerConfig
import com.example.api.handlers.GreetingHandler
import com.example.api.handlers.HealthHandler
import com.example.api.handlers.RootHandler
import io.javalin.config.JavalinConfig

/**
 * 注册 API 路由。
 *
 * @param config Javalin 配置对象
 * @param serverConfig 服务器配置
 */
fun registerApiRoutes(config: JavalinConfig, serverConfig: ServerConfig) {
    val rootHandler = RootHandler(serverConfig)

    config.routes.get("/") { ctx -> rootHandler.show(ctx) }
    config.routes.get("/api/health") { ctx -> HealthHandler.show(ctx) }
    config.routes.get("/api/hello") { ctx -> GreetingHandler.show(ctx) }
}

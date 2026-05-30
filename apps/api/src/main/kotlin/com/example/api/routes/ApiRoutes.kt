package com.example.api.routes

import com.example.api.config.ServerConfig
import com.example.api.handlers.DatabaseHealthHandler
import com.example.api.handlers.GreetingHandler
import com.example.api.handlers.HealthHandler
import com.example.api.handlers.RootHandler
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.config.JavalinConfig

/**
 * 注册 API 路由。
 *
 * @param config Javalin 配置实例
 * @param serverConfig 服务器配置
 */
fun registerApiRoutes(config: JavalinConfig, serverConfig: ServerConfig) {
    val rootHandler = RootHandler(serverConfig)

    config.routes.apiBuilder {
        get("/") { ctx -> rootHandler.show(ctx) }

        path("/api") {
            get("/health") { ctx -> HealthHandler.show(ctx) }
            get("/health/database") { ctx -> DatabaseHealthHandler.show(ctx) }
            get("/hello") { ctx -> GreetingHandler.show(ctx) }
        }
    }
}

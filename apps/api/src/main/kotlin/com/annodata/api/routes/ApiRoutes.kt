package com.annodata.api.routes

import com.annodata.api.config.SecurityConfig
import com.annodata.api.config.ServerConfig
import com.annodata.api.routes.auth.registerAuthRoutes
import com.annodata.api.routes.ai.registerAiAnnotationRoutes
import com.annodata.api.routes.datasets.registerDatasetRoutes
import com.annodata.api.routes.demo.registerDemoRoutes
import com.annodata.api.routes.health.registerHealthRoutes
import com.annodata.api.routes.system.registerSystemRoutes
import com.annodata.api.service.auth.AuthService
import com.annodata.api.middleware.auth.AuthMiddleware
import com.annodata.api.middleware.ai.AiWorkerAuthMiddleware
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.config.JavalinConfig

/**
 * 注册 API 路由。
 *
 * @param config Javalin 配置实例
 * @param serverConfig 服务器配置
 */
fun registerApiRoutes(config: JavalinConfig, serverConfig: ServerConfig) {
    registerApiRoutes(config, serverConfig, null)
}

/**
 * 注册 API 路由。
 *
 * @param config Javalin 配置实例
 * @param serverConfig 服务器配置
 * @param securityConfig 安全配置
 */
fun registerApiRoutes(config: JavalinConfig, serverConfig: ServerConfig, securityConfig: SecurityConfig?) {
    val authService = securityConfig?.let(::AuthService)
    val authMiddleware = authService?.let(::AuthMiddleware)
    val aiWorkerAuthMiddleware = securityConfig?.let { AiWorkerAuthMiddleware(it.aiWorkerToken) }

    config.routes.apiBuilder {
        registerSystemRoutes(serverConfig)

        path("/api") {
            registerHealthRoutes()
            registerDemoRoutes()
            registerAuthRoutes(authService)
            registerDatasetRoutes(authMiddleware)
            registerAiAnnotationRoutes(authMiddleware, aiWorkerAuthMiddleware)
        }
    }
}

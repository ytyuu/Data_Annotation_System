package com.example.api.routes

import com.example.api.config.SecurityConfig
import com.example.api.config.ServerConfig
import com.example.api.routes.auth.registerAuthRoutes
import com.example.api.routes.datasets.registerDatasetRoutes
import com.example.api.routes.demo.registerDemoRoutes
import com.example.api.routes.health.registerHealthRoutes
import com.example.api.routes.system.registerSystemRoutes
import com.example.api.service.auth.AuthService
import com.example.api.middleware.auth.AuthMiddleware
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

    config.routes.apiBuilder {
        registerSystemRoutes(serverConfig)

        path("/api") {
            registerHealthRoutes()
            registerDemoRoutes()
            registerAuthRoutes(authService)
            registerDatasetRoutes(authMiddleware)
        }
    }
}

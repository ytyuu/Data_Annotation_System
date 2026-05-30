package com.example.api.routes

import com.example.api.config.SecurityConfig
import com.example.api.config.ServerConfig
import com.example.api.handlers.auth.LoginHandler
import com.example.api.handlers.auth.RegisterHandler
import com.example.api.handlers.demo.GreetingHandler
import com.example.api.handlers.health.DatabaseHealthHandler
import com.example.api.handlers.health.HealthHandler
import com.example.api.handlers.system.RootHandler
import com.example.api.service.auth.AuthService
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
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
    val rootHandler = RootHandler(serverConfig)
    val authService = securityConfig?.let(::AuthService)
    val loginHandler = authService?.let(::LoginHandler)
    val registerHandler = authService?.let(::RegisterHandler)

    config.routes.apiBuilder {
        get("/") { ctx -> rootHandler.show(ctx) }

        path("/api") {
            get("/health") { ctx -> HealthHandler.show(ctx) }
            get("/health/database") { ctx -> DatabaseHealthHandler.show(ctx) }
            get("/hello") { ctx -> GreetingHandler.show(ctx) }
            post("/register") { ctx ->
                if (registerHandler == null) {
                    ctx.status(500).json(mapOf("message" to "注册服务未配置"))
                } else {
                    registerHandler.create(ctx)
                }
            }
            post("/login") { ctx ->
                if (loginHandler == null) {
                    ctx.status(500).json(mapOf("message" to "登录服务未配置"))
                } else {
                    loginHandler.create(ctx)
                }
            }
        }
    }
}

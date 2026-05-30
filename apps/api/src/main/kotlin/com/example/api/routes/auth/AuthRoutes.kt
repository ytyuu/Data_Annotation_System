/**
 * 认证相关路由注册。
 */
package com.example.api.routes.auth

import com.example.api.handlers.auth.LoginHandler
import com.example.api.handlers.auth.MeHandler
import com.example.api.handlers.auth.RegisterHandler
import com.example.api.middleware.auth.AuthMiddleware
import com.example.api.middleware.auth.requireAuth
import com.example.api.routes.routeGroup
import com.example.api.service.auth.AuthService
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Handler

/**
 * 注册认证相关路由（/register、/login、/me）。
 *
 * @param authService 认证服务，为 null 时登录/注册接口返回 500
 */
fun registerAuthRoutes(authService: AuthService?) {
    val authMiddleware = authService?.let(::AuthMiddleware)
    val loginHandler = authService?.let(::LoginHandler)
    val registerHandler = authService?.let(::RegisterHandler)

    routeGroup(requireAuth(authMiddleware)) {
        get("/me") { ctx -> MeHandler.show(ctx) }
    }

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

/**
 * 认证相关路由注册。
 */
package com.annodata.api.routes.auth

import com.annodata.api.handlers.auth.LoginHandler
import com.annodata.api.handlers.auth.MeHandler
import com.annodata.api.handlers.auth.RegisterHandler
import com.annodata.api.http.internalServerError
import com.annodata.api.middleware.auth.AuthMiddleware
import com.annodata.api.middleware.auth.requireAuth
import com.annodata.api.routes.routeGroup
import com.annodata.api.service.auth.AuthService
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
            ctx.internalServerError("注册服务未配置")
        } else {
            registerHandler.create(ctx)
        }
    }

    post("/login") { ctx ->
        if (loginHandler == null) {
            ctx.internalServerError("登录服务未配置")
        } else {
            loginHandler.create(ctx)
        }
    }
}

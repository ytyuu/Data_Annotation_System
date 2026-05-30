package com.example.api.middleware.auth

import com.example.api.middleware.RouteMiddleware
import com.example.api.models.ErrorResponse
import com.example.api.models.UserResponse
import com.example.api.service.auth.AuthResult
import com.example.api.service.auth.AuthService
import io.javalin.http.Context

/**
 * 认证中间件，负责从请求中提取并验证 JWT Token。
 *
 * @property authService 认证服务
 */
class AuthMiddleware(private val authService: AuthService) {
    /**
     * 验证请求中的 Authorization 头，提取当前用户信息。
     *
     * 验证成功时将 [UserResponse] 存入上下文属性 [CURRENT_USER_KEY]。
     *
     * @param ctx Javalin 请求上下文
     * @return 验证通过返回 true，否则已设置对应 HTTP 错误响应并返回 false
     */
    fun requireUser(ctx: Context): Boolean {
        return when (val result = authService.currentUser(ctx.header("Authorization"))) {
            is AuthResult.Success -> {
                ctx.attribute(CURRENT_USER_KEY, result.value)
                true
            }

            is AuthResult.BadRequest -> {
                ctx.status(400).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Unauthorized -> {
                ctx.status(401).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Forbidden -> {
                ctx.status(403).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Conflict -> {
                ctx.status(409).json(ErrorResponse(result.message))
                false
            }
        }
    }

    companion object {
        const val CURRENT_USER_KEY = "currentUser"
    }
}

/**
 * 从上下文中获取当前登录用户信息。
 *
 * 必须在 [AuthMiddleware.requireUser] 成功后调用。
 */
fun Context.currentUser(): UserResponse = attribute(AuthMiddleware.CURRENT_USER_KEY)!!

/**
 * 创建要求登录认证的路由中间件。
 *
 * @param authMiddleware 认证中间件实例，为 null 时返回 500
 * @return 路由中间件
 */
fun requireAuth(authMiddleware: AuthMiddleware?): RouteMiddleware {
    return { ctx, next ->
        if (authMiddleware == null) {
            ctx.status(500).json(ErrorResponse("认证服务未配置"))
        } else if (authMiddleware.requireUser(ctx)) {
            next()
        }
    }
}

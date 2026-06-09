package com.annodata.api.middleware.auth

import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.internalServerError
import com.annodata.api.http.unauthorized
import com.annodata.api.middleware.RouteMiddleware
import com.annodata.api.models.UserResponse
import com.annodata.api.http.Result
import com.annodata.api.service.auth.AuthService
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
            is Result.Success -> {
                ctx.attribute(CURRENT_USER_KEY, result.value)
                true
            }

            is Result.BadRequest -> {
                ctx.badRequest(result.message)
                false
            }

            is Result.Unauthorized -> {
                ctx.unauthorized(result.message)
                false
            }

            is Result.Forbidden -> {
                ctx.forbidden(result.message)
                false
            }

            is Result.Conflict -> {
                ctx.conflict(result.message)
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
 * 从上下文中获取当前登录用户信息，没有认证用户时返回 null。
 */
fun Context.currentUserOrNull(): UserResponse? = attribute(AuthMiddleware.CURRENT_USER_KEY)

/**
 * 创建要求登录认证的路由中间件。
 *
 * @param authMiddleware 认证中间件实例，为 null 时返回 500
 * @return 路由中间件
 */
fun requireAuth(authMiddleware: AuthMiddleware?): RouteMiddleware {
    return { ctx, next ->
        if (authMiddleware == null) {
            ctx.internalServerError("认证服务未配置")
        } else if (authMiddleware.requireUser(ctx)) {
            next()
        }
    }
}

/**
 * 创建要求当前用户具备指定角色之一的路由中间件。
 *
 * 应放在 [requireAuth] 之后使用，例如：
 * routeGroup(requireAuth(authMiddleware), requireRole("provider")) { ... }
 *
 * @param roles 允许访问的角色
 * @return 路由中间件
 */
fun requireRole(vararg roles: String): RouteMiddleware {
    val allowedRoles = roles
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    return { ctx, next ->
        val currentUser = ctx.currentUserOrNull()

        when {
            allowedRoles.isEmpty() -> ctx.internalServerError("未配置允许访问的角色")
            currentUser == null -> ctx.unauthorized("请先登录")
            currentUser.role in allowedRoles -> next()
            else -> ctx.forbidden("权限不足")
        }
    }
}

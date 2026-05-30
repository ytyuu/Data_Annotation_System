package com.example.api.middleware.auth

import com.example.api.middleware.chain.RouteMiddleware
import com.example.api.models.ErrorResponse

/**
 * 创建要求登录认证的中间件。
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

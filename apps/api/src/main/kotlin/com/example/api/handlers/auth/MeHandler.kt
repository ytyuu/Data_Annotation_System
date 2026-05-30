package com.example.api.handlers.auth

import com.example.api.middleware.auth.currentUser
import io.javalin.http.Context

/**
 * 当前登录用户请求处理器。
 */
object MeHandler {
    fun show(ctx: Context) {
        ctx.json(ctx.currentUser())
    }
}

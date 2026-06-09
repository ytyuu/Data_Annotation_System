package com.annodata.api.handlers.auth

import com.annodata.api.middleware.auth.currentUser
import io.javalin.http.Context

/**
 * 当前登录用户请求处理器。
 */
/**
 * 当前登录用户请求处理器。
 */
object MeHandler {
    /**
     * 处理 `/api/me` 请求，返回当前登录用户信息。
     *
     * @param ctx Javalin 请求上下文
     */
    fun show(ctx: Context) {
        ctx.json(ctx.currentUser())
    }
}

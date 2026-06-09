package com.annodata.api.handlers.demo

import com.annodata.api.models.GreetingResponse
import io.javalin.http.Context

/**
 * 问候请求处理器。
 */
object GreetingHandler {
    /**
     * 处理 `/api/hello` 请求，返回带名字的问候语。
     *
     * @param ctx Javalin 请求上下文
     */
    fun show(ctx: Context) {
        val name = ctx.queryParam("name")?.takeIf { it.isNotBlank() } ?: "world"
        ctx.json(GreetingResponse(message = "Good Morning, $name!"))
    }
}

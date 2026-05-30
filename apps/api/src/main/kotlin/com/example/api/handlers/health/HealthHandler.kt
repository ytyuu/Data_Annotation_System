package com.example.api.handlers.health

import com.example.api.models.HealthResponse
import io.javalin.http.Context

/**
 * 健康检查请求处理器。
 */
object HealthHandler {
    /**
     * 处理 `/api/health` 请求，返回服务健康状态。
     *
     * @param ctx Javalin 请求上下文
     */
    fun show(ctx: Context) {
        ctx.json(HealthResponse(status = "ok"))
    }
}

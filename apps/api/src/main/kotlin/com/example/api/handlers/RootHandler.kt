package com.example.api.handlers

import com.example.api.config.ServerConfig
import com.example.api.models.RootResponse
import io.javalin.http.Context
import java.time.Instant

/**
 * 根路径请求处理器。
 *
 * @property serverConfig 服务器配置
 */
class RootHandler(private val serverConfig: ServerConfig) {
    /**
     * 处理 `/` 请求，返回服务运行状态。
     *
     * @param ctx Javalin 请求上下文
     */
    fun show(ctx: Context) {
        ctx.json(
            RootResponse(
                message = "API server is running",
                port = serverConfig.port,
                timestamp = Instant.now().toString()
            )
        )
    }
}

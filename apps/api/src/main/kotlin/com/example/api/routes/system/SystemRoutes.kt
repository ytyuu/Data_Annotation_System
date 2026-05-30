/**
 * 系统路由注册。
 */
package com.example.api.routes.system

import com.example.api.config.ServerConfig
import com.example.api.handlers.system.RootHandler
import io.javalin.apibuilder.ApiBuilder.get

/**
 * 注册系统相关路由（/）。
 *
 * @param serverConfig 服务器配置
 */
fun registerSystemRoutes(serverConfig: ServerConfig) {
    val rootHandler = RootHandler(serverConfig)

    get("/") { ctx -> rootHandler.show(ctx) }
}

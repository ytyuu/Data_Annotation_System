package com.example.api

import com.example.api.config.ServerConfig
import com.example.api.http.configureHttp
import com.example.api.routes.registerApiRoutes
import io.javalin.Javalin

/**
 * 创建并配置 Javalin 应用实例。
 *
 * @param serverConfig 服务器配置
 * @return 配置完成的 Javalin 实例
 */
fun createApp(serverConfig: ServerConfig): Javalin =
    Javalin.create { config ->
        configureHttp(config)
        registerApiRoutes(config, serverConfig)
    }

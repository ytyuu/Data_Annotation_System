package com.example.api

import com.example.api.config.ServerConfig

/**
 * 应用入口。
 */
object Main {
    /**
     * 启动 API 服务。
     *
     * @param args 命令行参数，第一个参数可指定端口号
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val serverConfig = ServerConfig.from(args)
        createApp(serverConfig).start(serverConfig.port)
        println("API server started at http://localhost:${serverConfig.port}")
        Thread.currentThread().join()
    }
}

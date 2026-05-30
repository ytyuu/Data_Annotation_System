package com.example.api

import com.example.api.config.AppConfig
import com.example.api.db.DatabaseFactory

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
        val appConfig = AppConfig.from(args)

        DatabaseFactory.init(appConfig.database)
        createApp(appConfig.server).start(appConfig.server.port)
        println("API server started at http://localhost:${appConfig.server.port}")
        Thread.currentThread().join()
    }
}

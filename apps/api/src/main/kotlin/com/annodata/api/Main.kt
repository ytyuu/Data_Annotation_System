package com.annodata.api

import com.annodata.api.config.AppConfig
import com.annodata.api.db.DatabaseFactory

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
        createApp(appConfig).start(appConfig.server.port)
        printStartupInfo(appConfig.server.port)
        Thread.currentThread().join()
    }

    /** 打印 API 启动地址。 */
    private fun printStartupInfo(port: Int) {
        val startupInfo = """
        |   Local:   http://localhost:$port
        |   Health:  http://localhost:$port/api/health
        |   Docs:    http://localhost:$port/api/docs
        """.trimMargin()
        println()
        println(startupInfo)
    }
}

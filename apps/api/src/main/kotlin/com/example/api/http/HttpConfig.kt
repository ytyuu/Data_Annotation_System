package com.example.api.http

import io.javalin.config.JavalinConfig

/**
 * 配置 HTTP 相关设置，如 CORS 等。
 *
 * @param config Javalin 配置对象
 */
fun configureHttp(config: JavalinConfig) {
    config.bundledPlugins.enableCors { cors ->
        cors.addRule { rule -> rule.anyHost() }
    }
}

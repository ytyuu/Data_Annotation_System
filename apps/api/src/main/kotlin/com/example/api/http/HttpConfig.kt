package com.example.api.http

import io.javalin.config.JavalinConfig
import io.javalin.http.Context

/**
 * 配置 HTTP 相关设置，如 CORS 等。
 *
 * @param config Javalin 配置对象
 */
fun configureHttp(config: JavalinConfig) {
    config.bundledPlugins.enableCors { cors ->
        cors.addRule { rule -> rule.anyHost() }
    }
    config.requestLogger.http { ctx, executionTimeMs ->
        println(formatRequestTimingLog(ctx, executionTimeMs.toDouble()))
    }
}

private const val ResetColor = "\u001B[0m"
private const val GreenColor = "\u001B[32m"
private const val YellowColor = "\u001B[33m"
private const val OrangeColor = "\u001B[38;5;208m"
private const val RedColor = "\u001B[31m"

private fun formatRequestTimingLog(ctx: Context, elapsedMs: Double): String {
    val durationText = String.format("%.2fms", elapsedMs)
    val color = resolveTimingColor(elapsedMs)

    return buildString {
        append(color)
        append("[HTTP] ")
        append(ctx.method())
        append(' ')
        append(ctx.path())
        append(" -> ")
        append(ctx.statusCode())
        append(" (")
        append(durationText)
        append(')')
        append(ResetColor)
    }
}

private fun resolveTimingColor(elapsedMs: Double): String =
    when {
        elapsedMs < 50 -> GreenColor
        elapsedMs < 100 -> YellowColor
        elapsedMs < 200 -> OrangeColor
        else -> RedColor
    }

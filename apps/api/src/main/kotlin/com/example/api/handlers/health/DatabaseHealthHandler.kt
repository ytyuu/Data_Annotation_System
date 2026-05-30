package com.example.api.handlers.health

import com.example.api.models.DatabaseHealthResponse
import io.javalin.http.Context
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.system.measureTimeMillis

/**
 * 数据库健康检查请求处理器。
 */
object DatabaseHealthHandler {
    /**
     * 处理 `/api/health/database` 请求，通过轻量查询检查 PostgreSQL 连接。
     *
     * @param ctx Javalin 请求上下文
     */
    fun show(ctx: Context) {
        var queryResult = 0

        val latencyMs = try {
            measureTimeMillis {
                queryResult = transaction {
                    exec("select 1") { resultSet ->
                        if (resultSet.next()) resultSet.getInt(1) else 0
                    } ?: 0
                }
            }
        } catch (error: Exception) {
            ctx.status(503).json(
                DatabaseHealthResponse(
                    status = "error",
                    database = "postgresql",
                    latencyMs = 0,
                    message = error.message ?: "Database health check failed",
                )
            )
            return
        }

        if (queryResult == 1) {
            ctx.json(
                DatabaseHealthResponse(
                    status = "ok",
                    database = "postgresql",
                    latencyMs = latencyMs,
                )
            )
        } else {
            ctx.status(503).json(
                DatabaseHealthResponse(
                    status = "error",
                    database = "postgresql",
                    latencyMs = latencyMs,
                    message = "Unexpected database health check result",
                )
            )
        }
    }
}

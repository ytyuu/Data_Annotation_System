package com.annodata.api.middleware.ai

import com.annodata.api.http.internalServerError
import com.annodata.api.http.unauthorized
import com.annodata.api.middleware.RouteMiddleware
import io.javalin.http.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 校验 AI Worker 使用的服务端访问令牌。 */
class AiWorkerAuthMiddleware(private val configuredToken: String?) {
    fun requireWorker(ctx: Context): Boolean {
        val expectedToken = configuredToken
            ?: run {
                ctx.internalServerError("AI Worker 认证未配置")
                return false
            }

        val actualToken = extractBearerToken(ctx.header("Authorization"))
            ?: run {
                ctx.unauthorized("缺少 AI Worker 访问令牌")
                return false
            }

        if (!constantTimeEquals(expectedToken, actualToken)) {
            ctx.unauthorized("AI Worker 访问令牌无效")
            return false
        }

        return true
    }

    private fun extractBearerToken(header: String?): String? {
        val value = header?.trim() ?: return null
        if (!value.startsWith("Bearer ", ignoreCase = true)) return null
        return value.substringAfter(' ').trim().takeIf { it.isNotEmpty() }
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            actual.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

fun requireAiWorker(middleware: AiWorkerAuthMiddleware?): RouteMiddleware {
    return { ctx, next ->
        if (middleware == null) {
            ctx.internalServerError("AI Worker 认证服务未配置")
        } else if (middleware.requireWorker(ctx)) {
            next()
        }
    }
}

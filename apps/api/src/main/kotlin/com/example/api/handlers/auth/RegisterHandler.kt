package com.example.api.handlers.auth

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.models.RegisterRequest
import com.example.api.service.auth.AuthResult
import com.example.api.service.auth.AuthService
import io.javalin.http.Context

/**
 * 用户注册请求处理器。
 */
class RegisterHandler(private val authService: AuthService) {
    /**
     * 处理 `/api/register` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun create(ctx: Context) {
        val request = ctx.bodyAsClass(RegisterRequest::class.java)
        when (val result = authService.register(request)) {
            is AuthResult.Success -> ctx.status(201).json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }
}

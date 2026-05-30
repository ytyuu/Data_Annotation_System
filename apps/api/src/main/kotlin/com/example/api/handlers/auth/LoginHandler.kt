package com.example.api.handlers.auth

import com.example.api.models.ErrorResponse
import com.example.api.models.LoginRequest
import com.example.api.service.auth.AuthResult
import com.example.api.service.auth.AuthService
import io.javalin.http.Context

/**
 * 用户登录请求处理器。
 */
class LoginHandler(private val authService: AuthService) {
    /**
     * 处理 `/api/login` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun create(ctx: Context) {
        val request = ctx.bodyAsClass(LoginRequest::class.java)
        when (val result = authService.login(request)) {
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.status(400).json(ErrorResponse(result.message))
            is AuthResult.Unauthorized -> ctx.status(401).json(ErrorResponse(result.message))
            is AuthResult.Forbidden -> ctx.status(403).json(ErrorResponse(result.message))
            is AuthResult.Conflict -> ctx.status(409).json(ErrorResponse(result.message))
        }
    }
}

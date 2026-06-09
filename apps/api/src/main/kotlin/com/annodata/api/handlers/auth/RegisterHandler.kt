package com.annodata.api.handlers.auth

import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.unauthorized
import com.annodata.api.models.RegisterRequest
import com.annodata.api.http.Result
import com.annodata.api.service.auth.AuthService
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
            is Result.Success -> ctx.status(201).json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }
}

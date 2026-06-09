package com.annodata.api.handlers.auth

import com.annodata.api.http.badRequest
import com.annodata.api.http.conflict
import com.annodata.api.http.forbidden
import com.annodata.api.http.unauthorized
import com.annodata.api.models.LoginRequest
import com.annodata.api.http.Result
import com.annodata.api.service.auth.AuthService
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
            is Result.Success -> ctx.json(result.value)
            is Result.BadRequest -> ctx.badRequest(result.message)
            is Result.Unauthorized -> ctx.unauthorized(result.message)
            is Result.Forbidden -> ctx.forbidden(result.message)
            is Result.Conflict -> ctx.conflict(result.message)
        }
    }
}

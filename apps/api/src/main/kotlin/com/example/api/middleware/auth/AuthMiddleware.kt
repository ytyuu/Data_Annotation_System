package com.example.api.middleware.auth

import com.example.api.models.ErrorResponse
import com.example.api.models.UserResponse
import com.example.api.service.auth.AuthResult
import com.example.api.service.auth.AuthService
import io.javalin.http.Context

/**
 * 认证中间件工具。
 */
class AuthMiddleware(private val authService: AuthService) {
    fun requireUser(ctx: Context): Boolean {
        return when (val result = authService.currentUser(ctx.header("Authorization"))) {
            is AuthResult.Success -> {
                ctx.attribute(CURRENT_USER_KEY, result.value)
                true
            }

            is AuthResult.BadRequest -> {
                ctx.status(400).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Unauthorized -> {
                ctx.status(401).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Forbidden -> {
                ctx.status(403).json(ErrorResponse(result.message))
                false
            }

            is AuthResult.Conflict -> {
                ctx.status(409).json(ErrorResponse(result.message))
                false
            }
        }
    }

    companion object {
        const val CURRENT_USER_KEY = "currentUser"
    }
}

fun Context.currentUser(): UserResponse = attribute(AuthMiddleware.CURRENT_USER_KEY)!!

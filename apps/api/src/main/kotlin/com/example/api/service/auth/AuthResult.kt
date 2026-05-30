package com.example.api.service.auth

/**
 * 认证业务结果。
 */
sealed class AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>()
    data class BadRequest(val message: String) : AuthResult<Nothing>()
    data class Unauthorized(val message: String) : AuthResult<Nothing>()
    data class Forbidden(val message: String) : AuthResult<Nothing>()
    data class Conflict(val message: String) : AuthResult<Nothing>()
}

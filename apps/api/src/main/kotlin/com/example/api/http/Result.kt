package com.example.api.http

/**
 * 认证业务结果。
 */
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class BadRequest(val message: String) : Result<Nothing>()
    data class Unauthorized(val message: String) : Result<Nothing>()
    data class Forbidden(val message: String) : Result<Nothing>()
    data class Conflict(val message: String) : Result<Nothing>()
}
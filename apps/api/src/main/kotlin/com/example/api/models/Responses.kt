package com.example.api.models

/**
 * 根路径响应数据。
 *
 * @property message 状态消息
 * @property port 服务端口
 * @property timestamp 响应时间戳
 */
data class RootResponse(
    val message: String,
    val port: Int,
    val timestamp: String
)

/**
 * 健康检查响应数据。
 *
 * @property status 健康状态
 */
data class HealthResponse(val status: String)

/**
 * 数据库健康检查响应数据。
 *
 * @property status 数据库健康状态
 * @property database 数据库类型
 * @property latencyMs 检查耗时，单位毫秒
 * @property message 错误说明，健康时为空
 */
data class DatabaseHealthResponse(
    val status: String,
    val database: String,
    val latencyMs: Long,
    val message: String? = null,
)

/**
 * 注册请求数据。
 */
data class RegisterRequest(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val role: String = "",
)

/**
 * 用户响应数据，不包含密码哈希。
 */
data class UserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val status: String,
)

/**
 * 注册响应数据。
 */
data class RegisterResponse(
    val message: String,
    val user: UserResponse,
)

/**
 * API 错误响应数据。
 */
data class ErrorResponse(val message: String)

/**
 * 问候响应数据。
 *
 * @property message 问候消息
 */
data class GreetingResponse(val message: String)

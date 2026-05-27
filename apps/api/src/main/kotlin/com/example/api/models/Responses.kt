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
 * 问候响应数据。
 *
 * @property message 问候消息
 */
data class GreetingResponse(val message: String)

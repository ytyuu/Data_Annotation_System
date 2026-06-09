package com.annodata.api.models

/**
 * 注册请求数据。
 *
 * @property username 登录用户名
 * @property password 明文密码，仅用于请求输入
 * @property displayName 用户显示名称
 * @property role 注册角色，目前公开注册仅支持 provider 和 annotator
 */
data class RegisterRequest(
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val role: String = "",
)

/**
 * 用户响应数据，不包含密码哈希。
 *
 * @property id 用户 ID
 * @property username 登录用户名
 * @property displayName 用户显示名称
 * @property role 用户角色
 * @property status 账号状态
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
 *
 * @property message 注册结果消息
 * @property user 注册成功后的用户信息
 */
data class RegisterResponse(
    val message: String,
    val user: UserResponse,
)

/**
 * 登录请求数据。
 *
 * @property username 登录用户名
 * @property password 明文密码，仅用于请求输入
 * @property role 当前登录选择的角色
 */
data class LoginRequest(
    val username: String = "",
    val password: String = "",
    val role: String = "",
)

/**
 * 登录响应数据。
 *
 * @property token JWT 访问令牌
 * @property tokenType 令牌类型
 * @property expiresAt 令牌过期时间
 * @property user 登录成功后的用户信息
 */
data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresAt: String,
    val user: UserResponse,
)

package com.example.api.service.auth

import com.example.api.config.SecurityConfig
import com.example.api.db.UsersTable
import com.example.api.models.LoginRequest
import com.example.api.models.LoginResponse
import com.example.api.models.RegisterRequest
import com.example.api.models.RegisterResponse
import com.example.api.models.UserResponse
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 认证业务服务。
 */
class AuthService(securityConfig: SecurityConfig) {
    private val jwtService = JwtService(securityConfig)
    private val publicRoles = setOf("provider", "annotator")

    fun register(request: RegisterRequest): AuthResult<RegisterResponse> {
        val username = request.username.trim()
        val displayName = request.displayName.trim()
        val password = request.password
        val role = request.role.trim()

        val validationError = validateRegister(username, displayName, password, role)
        if (validationError != null) {
            return AuthResult.BadRequest(validationError)
        }

        return try {
            val user = transaction {
                val exists = UsersTable
                    .selectAll()
                    .where { UsersTable.username eq username }
                    .limit(1)
                    .any()

                if (exists) {
                    return@transaction null
                }

                val now = OffsetDateTime.now()
                val userId = UUID.randomUUID()
                val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

                UsersTable.insert {
                    it[id] = userId
                    it[UsersTable.username] = username
                    it[UsersTable.passwordHash] = passwordHash
                    it[UsersTable.displayName] = displayName
                    it[UsersTable.role] = role
                    it[status] = "active"
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                UserResponse(
                    id = userId.toString(),
                    username = username,
                    displayName = displayName,
                    role = role,
                    status = "active",
                )
            }

            if (user == null) {
                AuthResult.Conflict("用户名已存在")
            } else {
                AuthResult.Success(RegisterResponse(message = "注册成功", user = user))
            }
        } catch (error: ExposedSQLException) {
            AuthResult.Conflict("用户名已存在或注册信息不符合要求")
        }
    }

    fun login(request: LoginRequest): AuthResult<LoginResponse> {
        val username = request.username.trim()
        val password = request.password
        val role = request.role.trim()

        if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
            return AuthResult.BadRequest("请输入用户名、密码和登录身份")
        }

        val loginUser = transaction {
            UsersTable
                .selectAll()
                .where { UsersTable.username eq username }
                .limit(1)
                .firstOrNull()
                ?.let { row ->
                    val passwordHash = row[UsersTable.passwordHash]
                    val passwordMatches = runCatching { BCrypt.checkpw(password, passwordHash) }.getOrDefault(false)
                    if (!passwordMatches) {
                        return@let null
                    }

                    UserResponse(
                        id = row[UsersTable.id].toString(),
                        username = row[UsersTable.username],
                        displayName = row[UsersTable.displayName],
                        role = row[UsersTable.role],
                        status = row[UsersTable.status],
                    )
                }
        }

        if (loginUser == null) {
            return AuthResult.Unauthorized("用户名或密码错误")
        }

        if (loginUser.status != "active") {
            return AuthResult.Forbidden("账号已被禁用")
        }

        if (loginUser.role != role) {
            return AuthResult.Forbidden("账号身份与当前登录身份不匹配")
        }

        val issuedToken = jwtService.issue(loginUser)
        return AuthResult.Success(
            LoginResponse(
                token = issuedToken.token,
                expiresAt = issuedToken.expiresAt.toString(),
                user = loginUser,
            )
        )
    }

    private fun validateRegister(username: String, displayName: String, password: String, role: String): String? {
        return when {
            username.length !in 3..64 -> "用户名长度必须为 3 到 64 个字符"
            !username.matches(Regex("^[A-Za-z0-9_]+$")) -> "用户名只能包含字母、数字和下划线"
            displayName.length !in 1..80 -> "显示名称长度必须为 1 到 80 个字符"
            password.length < 6 -> "密码至少需要 6 个字符"
            role !in publicRoles -> "只能注册数据集提供者或数据标注员"
            else -> null
        }
    }
}

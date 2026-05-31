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
/**
 * 认证业务服务，封装用户注册、登录和Token验证逻辑。
 *
 * @param securityConfig 安全配置
 */
class AuthService(securityConfig: SecurityConfig) {
    private val jwtService = JwtService(securityConfig)
    private val publicRoles = setOf("provider", "annotator")

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册结果
     */
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
                // 查询用户名是否已存在，避免重复注册。
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
        } catch (_: ExposedSQLException) {
            AuthResult.Conflict("用户名已存在或注册信息不符合要求")
        }
    }

    /**
     * 用户登录，验证成功后签发 JWT。
     *
     * @param request 登录请求
     * @return 登录结果，成功时包含 JWT Token
     */
    fun login(request: LoginRequest): AuthResult<LoginResponse> {
        val username = request.username.trim()
        val password = request.password
        val role = request.role.trim()

        if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
            return AuthResult.BadRequest("请输入用户名、密码和登录身份")
        }

        val loginUser = transaction {
            // 查询登录用户名对应的账号记录，再在内存中校验密码。
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

    /**
     * 从 Authorization 头中提取并验证 JWT，返回当前用户信息。
     *
     * @param authorizationHeader Authorization 请求头（Bearer xxx）
     * @return 验证结果，成功时返回用户信息
     */
    fun currentUser(authorizationHeader: String?): AuthResult<UserResponse> {
        val token = extractBearerToken(authorizationHeader)
            ?: return AuthResult.Unauthorized("缺少访问令牌")

        val verification = jwtService.verify(token)
        if (verification is JwtVerificationResult.Invalid) {
            return AuthResult.Unauthorized(verification.message)
        }

        val claims = (verification as JwtVerificationResult.Valid).claims
        val userId = runCatching { UUID.fromString(claims.userId) }.getOrNull()
            ?: return AuthResult.Unauthorized("Token 用户 ID 无效")

        val user = transaction {
            // 根据 Token 中的用户 ID 查询当前用户，确保账号仍然存在。
            UsersTable
                .selectAll()
                .where { UsersTable.id eq userId }
                .limit(1)
                .firstOrNull()
                ?.let { row ->
                    UserResponse(
                        id = row[UsersTable.id].toString(),
                        username = row[UsersTable.username],
                        displayName = row[UsersTable.displayName],
                        role = row[UsersTable.role],
                        status = row[UsersTable.status],
                    )
                }
        } ?: return AuthResult.Unauthorized("用户不存在")

        if (user.status != "active") {
            return AuthResult.Forbidden("账号已被禁用")
        }

        if (user.role != claims.role) {
            return AuthResult.Unauthorized("Token 角色信息无效")
        }

        return AuthResult.Success(user)
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

    private fun extractBearerToken(authorizationHeader: String?): String? {
        val header = authorizationHeader?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) {
            return null
        }

        return header.substringAfter(' ').trim().takeIf { it.isNotEmpty() }
    }
}

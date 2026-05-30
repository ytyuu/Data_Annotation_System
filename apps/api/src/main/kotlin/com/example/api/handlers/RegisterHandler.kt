package com.example.api.handlers

import com.example.api.db.UsersTable
import com.example.api.models.ErrorResponse
import com.example.api.models.RegisterRequest
import com.example.api.models.RegisterResponse
import com.example.api.models.UserResponse
import io.javalin.http.Context
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 用户注册请求处理器。
 */
object RegisterHandler {
    private val publicRoles = setOf("provider", "annotator")

    /**
     * 处理 `/api/register` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun create(ctx: Context) {
        val request = ctx.bodyAsClass(RegisterRequest::class.java)
        val username = request.username.trim()
        val displayName = request.displayName.trim()
        val password = request.password
        val role = request.role.trim()

        val validationError = validate(username, displayName, password, role)
        if (validationError != null) {
            ctx.status(400).json(ErrorResponse(validationError))
            return
        }

        try {
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
                ctx.status(409).json(ErrorResponse("用户名已存在"))
                return
            }

            ctx.status(201).json(RegisterResponse(message = "注册成功", user = user))
        } catch (error: ExposedSQLException) {
            ctx.status(409).json(ErrorResponse("用户名已存在或注册信息不符合要求"))
        }
    }

    private fun validate(username: String, displayName: String, password: String, role: String): String? {
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

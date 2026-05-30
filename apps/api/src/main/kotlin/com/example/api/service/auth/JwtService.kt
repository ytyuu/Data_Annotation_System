package com.example.api.service.auth

import com.example.api.config.SecurityConfig
import com.example.api.models.UserResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 签发 HMAC-SHA256 JWT。
 */
/**
 * 签发和验证 HMAC-SHA256 JWT。
 *
 * @param config 安全配置
 */
class JwtService(private val config: SecurityConfig) {
    /**
     * 为用户签发 JWT。
     *
     * @param user 用户信息
     * @return 签发的 Token
     */
    fun issue(user: UserResponse): IssuedToken {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = now.plusMinutes(config.jwtExpiresMinutes)
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = buildPayload(user, now, expiresAt)
        val unsignedToken = "${base64Url(header)}.${base64Url(payload)}"
        val signature = sign(unsignedToken)

        return IssuedToken(
            token = "$unsignedToken.$signature",
            expiresAt = expiresAt,
        )
    }

    /**
     * 验证 JWT 的签名和有效期。
     *
     * @param token JWT 字符串
     * @return 验证结果
     */
    fun verify(token: String): JwtVerificationResult {
        val parts = token.split('.')
        if (parts.size != 3) {
            return JwtVerificationResult.Invalid("Token 格式无效")
        }

        val unsignedToken = "${parts[0]}.${parts[1]}"
        val expectedSignature = sign(unsignedToken)
        if (!MessageDigest.isEqual(expectedSignature.toByteArray(), parts[2].toByteArray())) {
            return JwtVerificationResult.Invalid("Token 签名无效")
        }

        val payload = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
        }.getOrElse {
            return JwtVerificationResult.Invalid("Token 内容无效")
        }

        val expiresAt = extractLong(payload, "exp")
            ?: return JwtVerificationResult.Invalid("Token 缺少过期时间")
        val now = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond()
        if (expiresAt <= now) {
            return JwtVerificationResult.Invalid("Token 已过期")
        }

        val claims = JwtClaims(
            userId = extractString(payload, "sub") ?: return JwtVerificationResult.Invalid("Token 缺少用户 ID"),
            username = extractString(payload, "username") ?: return JwtVerificationResult.Invalid("Token 缺少用户名"),
            displayName = extractString(payload, "displayName") ?: return JwtVerificationResult.Invalid("Token 缺少显示名称"),
            role = extractString(payload, "role") ?: return JwtVerificationResult.Invalid("Token 缺少角色"),
            expiresAt = expiresAt,
        )

        return JwtVerificationResult.Valid(claims)
    }

    private fun buildPayload(user: UserResponse, issuedAt: OffsetDateTime, expiresAt: OffsetDateTime): String {
        return buildString {
            append('{')
            appendJson("iss", config.jwtIssuer)
            append(',')
            appendJson("sub", user.id)
            append(',')
            appendJson("username", user.username)
            append(',')
            appendJson("displayName", user.displayName)
            append(',')
            appendJson("role", user.role)
            append(',')
            append("\"iat\":").append(issuedAt.toEpochSecond())
            append(',')
            append("\"exp\":").append(expiresAt.toEpochSecond())
            append('}')
        }
    }

    private fun StringBuilder.appendJson(key: String, value: String) {
        append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"')
    }

    private fun escapeJson(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun base64Url(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(config.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun extractString(payload: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return pattern.find(payload)?.groupValues?.get(1)?.unescapeJson()
    }

    private fun extractLong(payload: String, key: String): Long? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        return pattern.find(payload)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun String.unescapeJson(): String {
        return replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\b", "\b")
    }
}

/**
 * 已签发的 JWT。
 *
 * @property token JWT 字符串
 * @property expiresAt 过期时间
 */
data class IssuedToken(
    val token: String,
    val expiresAt: OffsetDateTime,
)

/**
 * JWT Payload 中的用户声明。
 *
 * @property userId 用户 ID
 * @property username 用户名
 * @property displayName 显示名称
 * @property role 角色
 * @property expiresAt 过期时间戳（秒）
 */
data class JwtClaims(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
    val expiresAt: Long,
)

/** JWT 验证结果。 */
sealed class JwtVerificationResult {
    /** 验证通过。 */
    data class Valid(val claims: JwtClaims) : JwtVerificationResult()

    /** 验证失败，[message] 为失败原因。 */
    data class Invalid(val message: String) : JwtVerificationResult()
}

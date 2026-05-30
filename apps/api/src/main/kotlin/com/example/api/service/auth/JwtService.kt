package com.example.api.service.auth

import com.example.api.config.SecurityConfig
import com.example.api.models.UserResponse
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 签发 HMAC-SHA256 JWT。
 */
class JwtService(private val config: SecurityConfig) {
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
}

data class IssuedToken(
    val token: String,
    val expiresAt: OffsetDateTime,
)

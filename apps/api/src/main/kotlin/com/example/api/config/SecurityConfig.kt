package com.example.api.config

/**
 * 安全配置。
 */
data class SecurityConfig(
    val jwtSecret: String,
) {
    companion object {
        private const val DEFAULT_JWT_SECRET = "change-me-in-env"

        fun from(env: Map<String, String>): SecurityConfig {
            return SecurityConfig(
                jwtSecret = env["JWT_SECRET"]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_JWT_SECRET,
            )
        }
    }
}

package com.annodata.api.config

/**
 * 安全配置。
 */
data class SecurityConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtExpiresMinutes: Long,
    val aiWorkerToken: String?,
    val aiWorkerBaseUrl: String?,
    val workerTriggerToken: String?,
) {
    companion object {
        private const val DEFAULT_JWT_SECRET = "change-me-in-env"
        private const val DEFAULT_JWT_ISSUER = "data-annotation-api"
        private const val DEFAULT_JWT_EXPIRES_MINUTES = 120L

        fun from(env: Map<String, String>): SecurityConfig {
            return SecurityConfig(
                jwtSecret = env["JWT_SECRET"]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_JWT_SECRET,
                jwtIssuer = env["JWT_ISSUER"]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_JWT_ISSUER,
                jwtExpiresMinutes = env["JWT_EXPIRES_MINUTES"]?.trim()?.toLongOrNull()
                    ?: DEFAULT_JWT_EXPIRES_MINUTES,
                aiWorkerToken = env["AI_WORKER_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() },
                aiWorkerBaseUrl = env["AI_WORKER_BASE_URL"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() },
                workerTriggerToken = env["WORKER_TRIGGER_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

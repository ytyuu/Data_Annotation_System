package com.annodata.api.config

/**
 * PostgreSQL 数据库配置。
 */
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
) {
    val jdbcUrl: String = "jdbc:postgresql://$host:$port/$database"

    companion object {
        private const val DEFAULT_HOST = "localhost"
        private const val DEFAULT_PORT = 5432
        private const val DEFAULT_DATABASE = "data_annotation"
        private const val DEFAULT_USERNAME = "postgres"
        private const val DEFAULT_PASSWORD = "postgres"
        private const val DEFAULT_MAXIMUM_POOL_SIZE = 10

        fun from(env: Map<String, String>): DatabaseConfig {
            return DatabaseConfig(
                host = env["DB_HOST"].orDefault(DEFAULT_HOST),
                port = env["DB_PORT"].toIntOrDefault(DEFAULT_PORT),
                database = env["DB_NAME"].orDefault(DEFAULT_DATABASE),
                username = env["DB_USER"].orDefault(DEFAULT_USERNAME),
                password = env["DB_PASSWORD"].orDefault(DEFAULT_PASSWORD),
                maximumPoolSize = env["DB_MAX_POOL_SIZE"].toIntOrDefault(DEFAULT_MAXIMUM_POOL_SIZE),
            )
        }
    }
}

private fun String?.orDefault(defaultValue: String): String {
    return this?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue
}

private fun String?.toIntOrDefault(defaultValue: Int): Int {
    return this?.trim()?.toIntOrNull() ?: defaultValue
}

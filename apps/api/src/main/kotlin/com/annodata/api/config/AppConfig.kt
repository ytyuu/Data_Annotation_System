package com.annodata.api.config

/**
 * 应用配置。
 *
 * @property server HTTP 服务配置
 * @property database 数据库配置
 * @property security 安全相关配置
 */
data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val security: SecurityConfig,
) {
    companion object {
        /**
         * 从命令行参数、环境变量和根目录 `.env` 解析应用配置。
         */
        fun from(args: Array<String>): AppConfig {
            val env = EnvLoader.load()

            return AppConfig(
                server = ServerConfig.from(args, env),
                database = DatabaseConfig.from(env),
                security = SecurityConfig.from(env),
            )
        }
    }
}

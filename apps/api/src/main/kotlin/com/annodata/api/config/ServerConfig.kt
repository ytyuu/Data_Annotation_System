package com.annodata.api.config

/**
 * 服务器配置。
 *
 * @property port 服务监听端口
 */
data class ServerConfig(val port: Int) {
    companion object {
        private const val DEFAULT_PORT = 7000

        /**
         * 从命令行参数和环境变量解析配置。
         *
         * @param args 命令行参数，第一个元素若为有效端口号则优先使用
         * @param env 环境变量映射，默认使用 [System.getenv]
         * @return 解析后的 [ServerConfig]
         */
        fun from(args: Array<String>, env: Map<String, String> = System.getenv()): ServerConfig {
            val port = parsePort(args.firstOrNull())
                ?: parsePort(env["PORT"])
                ?: DEFAULT_PORT

            return ServerConfig(port)
        }

        private fun parsePort(raw: String?): Int? {
            val port = raw?.trim()?.toIntOrNull() ?: return null
            return port.takeIf { it in 1..65535 }
        }
    }
}

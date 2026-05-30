package com.example.api.db

import com.example.api.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * 初始化 Exposed 使用的 PostgreSQL 连接池。
 */
object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun init(config: DatabaseConfig) {
        if (dataSource != null) {
            return
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.maximumPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            initializationFailTimeout = -1
            poolName = "data-annotation-api"
        }

        dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource!!)
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}

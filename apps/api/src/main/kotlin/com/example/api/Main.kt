package com.example.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant

object Main {
    private const val DEFAULT_PORT = 7000

    @JvmStatic
    fun main(args: Array<String>) {
        val port = resolvePort(args)
        val server = HttpServer.create(InetSocketAddress(port), 0)

        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                sendNoContent(exchange)
                return@createContext
            }

            if (path == "/") {
                sendJson(
                    exchange,
                    200,
                    """{"message":"API server is running","port":$port,"timestamp":"${Instant.now()}"}"""
                )
                return@createContext
            }

            sendJson(exchange, 404, """{"error":"Not Found","path":"${escapeJson(path)}"}""")
        }

        server.createContext("/api/health") { exchange ->
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                sendNoContent(exchange)
                return@createContext
            }
            sendJson(exchange, 200, """{"status":"ok"}""")
        }

        server.createContext("/api/hello") { exchange ->
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                sendNoContent(exchange)
                return@createContext
            }
            val name = queryParam(exchange.requestURI, "name", "world")
            sendJson(exchange, 200, """{"message":"Good Morning, ${escapeJson(name)}!"}""")
        }

        Runtime.getRuntime().addShutdownHook(Thread { server.stop(0) })
        server.start()
        println("API server started at http://localhost:$port")
        Thread.currentThread().join()
    }

    private fun resolvePort(args: Array<String>): Int {
        args.firstOrNull()?.let { parsePort(it) }?.let { return it }
        return parsePort(System.getenv("PORT")) ?: DEFAULT_PORT
    }

    private fun parsePort(raw: String?): Int? {
        val port = raw?.trim()?.toIntOrNull() ?: return null
        return port.takeIf { it in 1..65535 }
    }

    @Throws(IOException::class)
    private fun sendNoContent(exchange: HttpExchange) {
        addCorsHeaders(exchange)
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
    }

    @Throws(IOException::class)
    private fun sendJson(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        addCorsHeaders(exchange)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { outputStream -> outputStream.write(bytes) }
    }

    private fun addCorsHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "content-type")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET,OPTIONS")
    }

    private fun queryParam(uri: URI, key: String, defaultValue: String): String {
        val rawQuery = uri.rawQuery
        if (rawQuery.isNullOrBlank()) {
            return defaultValue
        }

        for (pair in rawQuery.split("&")) {
            val index = pair.indexOf('=')
            val candidateKey = if (index >= 0) pair.substring(0, index) else pair
            if (candidateKey != key) {
                continue
            }

            val value = if (index >= 0) pair.substring(index + 1) else ""
            val decoded = URLDecoder.decode(value, StandardCharsets.UTF_8)
            return decoded.takeUnless { it.isBlank() } ?: defaultValue
        }

        return defaultValue
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch < ' ') {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }
}

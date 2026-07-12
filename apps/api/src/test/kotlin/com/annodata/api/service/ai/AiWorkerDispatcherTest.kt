package com.annodata.api.service.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class AiWorkerDispatcherTest {
    private val batchId = UUID.fromString("00000000-0000-4000-8000-000000000100")

    @Test
    fun `dispatches authenticated json request and accepts 202`() {
        var authorization: String? = null
        var requestBody = ""
        withServer(202, """{"message":"accepted"}""") { baseUrl, server ->
            server.removeContext("/run")
            server.createContext("/run") { exchange ->
                authorization = exchange.requestHeaders.getFirst("Authorization")
                requestBody = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
                respond(exchange, 202, """{"message":"accepted"}""")
            }

            val outcome = dispatcher(baseUrl).dispatch(batchId)

            assertEquals(AiWorkerDispatchOutcome.Accepted, outcome)
            assertEquals("Bearer trigger-token", authorization)
            assertTrue(requestBody.contains(batchId.toString()))
        }
    }

    @Test
    fun `preserves worker rejection status and message`() {
        withServer(409, """{"message":"already running"}""") { baseUrl, _ ->
            val outcome = dispatcher(baseUrl).dispatch(batchId)
            val rejected = assertInstanceOf(AiWorkerDispatchOutcome.Rejected::class.java, outcome)

            assertEquals(409, rejected.statusCode)
            assertEquals("already running", rejected.message)
        }
    }

    @Test
    fun `reports unreachable worker separately`() {
        val port = ServerSocket(0).use { it.localPort }

        val outcome = dispatcher("http://127.0.0.1:$port").dispatch(batchId)

        assertInstanceOf(AiWorkerDispatchOutcome.Unavailable::class.java, outcome)
    }

    @Test
    fun `reports malformed base url as configuration error`() {
        val outcome = dispatcher("not a valid url").dispatch(batchId)

        assertInstanceOf(AiWorkerDispatchOutcome.ConfigurationError::class.java, outcome)
    }

    private fun dispatcher(baseUrl: String) = AiWorkerDispatcher(
        baseUrl = baseUrl,
        triggerToken = "trigger-token",
        requestTimeout = Duration.ofSeconds(1),
    )

    private fun withServer(
        status: Int,
        body: String,
        block: (String, HttpServer) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/run") { exchange -> respond(exchange, status, body) }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", server)
        } finally {
            server.stop(0)
        }
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

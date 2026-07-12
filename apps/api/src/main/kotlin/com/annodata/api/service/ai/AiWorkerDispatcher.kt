package com.annodata.api.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

class AiWorkerDispatcher(
    private val baseUrl: String,
    private val triggerToken: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(5),
) {
    private val objectMapper = ObjectMapper()

    fun dispatch(batchId: UUID): AiWorkerDispatchOutcome {
        val endpoint = runCatching { URI.create("${baseUrl.trimEnd('/')}/run") }.getOrNull()
            ?: return AiWorkerDispatchOutcome.ConfigurationError("AI_WORKER_BASE_URL 不是合法地址")
        val body = objectMapper.writeValueAsString(mapOf("batchId" to batchId.toString()))
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer $triggerToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                AiWorkerDispatchOutcome.Accepted
            } else {
                AiWorkerDispatchOutcome.Rejected(
                    statusCode = response.statusCode(),
                    message = extractMessage(response.body()) ?: "Worker 返回 HTTP ${response.statusCode()}",
                )
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            AiWorkerDispatchOutcome.Unavailable("连接 Worker 的请求被中断")
        } catch (error: IOException) {
            AiWorkerDispatchOutcome.Unavailable(error.message ?: "无法连接 Worker")
        } catch (error: IllegalArgumentException) {
            AiWorkerDispatchOutcome.ConfigurationError("AI_WORKER_BASE_URL 不是合法地址")
        }
    }

    private fun extractMessage(body: String): String? {
        return runCatching { objectMapper.readTree(body).path("message").asText().trim() }
            .getOrNull()
            ?.takeIf(String::isNotEmpty)
    }
}

sealed class AiWorkerDispatchOutcome {
    data object Accepted : AiWorkerDispatchOutcome()
    data class ConfigurationError(val message: String) : AiWorkerDispatchOutcome()
    data class Unavailable(val message: String) : AiWorkerDispatchOutcome()
    data class Rejected(val statusCode: Int, val message: String) : AiWorkerDispatchOutcome()
}

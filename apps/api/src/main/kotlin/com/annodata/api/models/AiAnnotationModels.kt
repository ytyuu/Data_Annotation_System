package com.annodata.api.models

import com.fasterxml.jackson.databind.JsonNode

data class CreateAiAnnotationBatchRequest(
    val maxItems: Int = 1000,
    val modelName: String = "deepseek-v4-flash",
    val promptVersion: String = "classification-v1",
    val confidenceThreshold: Double = 0.85,
    val samplingRatio: Double = 0.1,
    val highRiskOptionValues: List<String> = emptyList(),
    val metadataAllowList: List<String> = emptyList(),
    val maxAttempts: Int = 3,
)

data class AiAnnotationBatchResponse(
    val id: String,
    val datasetId: String,
    val datasetName: String,
    val providerId: String,
    val status: String,
    val modelProvider: String,
    val modelName: String,
    val promptVersion: String,
    val config: JsonNode,
    val totalCount: Int,
    val processedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val needsReviewCount: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val modelRequestCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val errorMessage: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class AiAnnotationBatchListResponse(
    val items: List<AiAnnotationBatchResponse>,
)

data class AiAnnotationWorkItemResponse(
    val resultId: String,
    val itemId: String,
    val roundNo: Int,
    val content: String,
    val contentType: String,
    val metadata: JsonNode,
)

data class AiAnnotationWorkResponse(
    val batchId: String,
    val datasetId: String,
    val modelName: String,
    val promptVersion: String,
    val annotationGuide: String?,
    val annotationSchema: JsonNode,
    val config: JsonNode,
    val items: List<AiAnnotationWorkItemResponse>,
)

data class SubmitAiAnnotationResultsRequest(
    val requestId: String = "",
    val chunkNo: Int = 0,
    val modelRequestCount: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val items: List<AiAnnotationResultInput> = emptyList(),
)

data class AiAnnotationResultInput(
    val resultId: String = "",
    val itemId: String = "",
    val roundNo: Int = 1,
    val result: JsonNode? = null,
    val confidence: String? = null,
    val confidenceScore: Double? = null,
    val reason: String? = null,
    val needsHumanReview: Boolean = false,
    val rawOutput: JsonNode? = null,
    val errorMessage: String? = null,
)

data class SubmitAiAnnotationResultsResponse(
    val acceptedCount: Int,
    val failedCount: Int,
    val batchStatus: String,
)

data class FailAiAnnotationBatchRequest(
    val errorMessage: String = "",
)

data class AiAnnotationResultResponse(
    val id: String,
    val batchId: String,
    val datasetId: String,
    val itemId: String,
    val roundNo: Int,
    val status: String,
    val content: String,
    val contentType: String,
    val metadata: JsonNode,
    val result: JsonNode?,
    val acceptedResult: JsonNode?,
    val confidence: String?,
    val confidenceScore: String?,
    val reason: String?,
    val needsHumanReview: Boolean,
    val isSampled: Boolean,
    val riskFlags: JsonNode,
    val errorMessage: String?,
    val attemptCount: Int,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val reviewAction: String?,
    val reviewComment: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class AiAnnotationResultListResponse(
    val items: List<AiAnnotationResultResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

data class ReviewAiAnnotationResultRequest(
    val action: String = "",
    val acceptedResult: JsonNode? = null,
    val comment: String? = null,
)

data class BatchReviewAiAnnotationResultsRequest(
    val batchId: String = "",
    val resultIds: List<String> = emptyList(),
    val action: String = "accept",
    val comment: String? = null,
)

data class ReviewAiAnnotationResultResponse(
    val message: String,
    val affectedCount: Int,
)

data class MessageResponse(val message: String)

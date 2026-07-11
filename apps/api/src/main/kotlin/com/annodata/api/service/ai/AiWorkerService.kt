package com.annodata.api.service.ai

import com.annodata.api.db.AiAnnotationBatchesTable
import com.annodata.api.db.AiAnnotationResultsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.http.Result
import com.annodata.api.models.AiAnnotationResultInput
import com.annodata.api.models.AiAnnotationWorkItemResponse
import com.annodata.api.models.AiAnnotationWorkResponse
import com.annodata.api.models.FailAiAnnotationBatchRequest
import com.annodata.api.models.MessageResponse
import com.annodata.api.models.SubmitAiAnnotationResultsRequest
import com.annodata.api.models.SubmitAiAnnotationResultsResponse
import com.annodata.api.service.ai.store.AiAnnotationBatchCounter
import com.annodata.api.service.ai.store.AiAnnotationStore
import com.annodata.api.service.ai.validation.AiAnnotationValidator
import com.annodata.api.service.ai.validation.AiResultQuality
import com.annodata.api.service.ai.validation.AiResultRiskEvaluator
import com.annodata.api.service.ai.validation.ClassificationSchema
import com.annodata.api.service.ai.validation.ClassificationSchemaParseResult
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class AiWorkerService {
    private val store = AiAnnotationStore()
    private val objectMapper = ObjectMapper()

    fun claimItems(batchId: UUID, limit: Int): Result<AiAnnotationWorkResponse> {
        if (limit !in 1..500) return Result.BadRequest("领取数量必须在 1 到 500 之间")

        val outcome = transaction {
            val batch = store.findBatch(batchId) ?: return@transaction WorkerClaimOutcome.NotFound
            val batchStatus = batch[AiAnnotationBatchesTable.status]
            if (batchStatus !in setOf("pending", "running", "completed")) {
                return@transaction WorkerClaimOutcome.InvalidStatus
            }
            val schemaResult = AiAnnotationValidator.parseSchema(batch[AiAnnotationBatchesTable.annotationSchemaSnapshot])
            if (schemaResult is ClassificationSchemaParseResult.Invalid) {
                return@transaction WorkerClaimOutcome.InvalidContext(schemaResult.message)
            }
            val config = parseWorkerConfig(batch[AiAnnotationBatchesTable.config])
            val now = OffsetDateTime.now()
            val claimed = if (batchStatus == "completed") {
                emptyList()
            } else {
                store.markExhaustedLeasesFailed(batchId, config.maxAttempts, now)
                store.claimWorkItems(
                    batchId = batchId,
                    maxAttempts = config.maxAttempts,
                    limit = limit,
                    now = now,
                    leaseExpiresAt = now.plusMinutes(5),
                )
            }

            if (claimed.isNotEmpty()) {
                store.markBatchRunning(batchId, batch[AiAnnotationBatchesTable.startedAt] == null, now)
            }
            val counts = AiAnnotationBatchCounter.refresh(batchId, now)
            if (claimed.isEmpty() && counts.total > 0 && counts.processed == counts.total) {
                store.markBatchCompleted(batchId, now)
            }

            WorkerClaimOutcome.Success(
                AiAnnotationWorkResponse(
                    batchId = batchId.toString(),
                    datasetId = batch[AiAnnotationBatchesTable.datasetId].toString(),
                    modelName = batch[AiAnnotationBatchesTable.modelName],
                    promptVersion = batch[AiAnnotationBatchesTable.promptVersion],
                    annotationGuide = batch[AiAnnotationBatchesTable.annotationGuideSnapshot],
                    annotationSchema = objectMapper.readTree(batch[AiAnnotationBatchesTable.annotationSchemaSnapshot]),
                    config = objectMapper.readTree(batch[AiAnnotationBatchesTable.config]),
                    items = claimed.map { item ->
                        AiAnnotationWorkItemResponse(
                            resultId = item.resultId.toString(),
                            itemId = item.itemId.toString(),
                            roundNo = item.roundNo,
                            content = item.content,
                            contentType = item.contentType,
                            metadata = filterMetadata(item.metadata, config.metadataAllowList),
                        )
                    },
                )
            )
        }

        return when (outcome) {
            WorkerClaimOutcome.NotFound -> Result.BadRequest("AI 标注批次不存在")
            WorkerClaimOutcome.InvalidStatus -> Result.Conflict("当前批次状态不允许领取数据")
            is WorkerClaimOutcome.InvalidContext -> Result.BadRequest(outcome.message)
            is WorkerClaimOutcome.Success -> Result.Success(outcome.value)
        }
    }

    fun submitResults(
        batchId: UUID,
        request: SubmitAiAnnotationResultsRequest,
    ): Result<SubmitAiAnnotationResultsResponse> {
        validateSubmissionRequest(request)?.let { return Result.BadRequest(it) }

        val outcome = transaction {
            val batch = store.findBatch(batchId) ?: return@transaction SubmitResultsOutcome.NotFound
            if (batch[AiAnnotationBatchesTable.status] !in setOf("pending", "running")) {
                return@transaction SubmitResultsOutcome.InvalidBatchStatus
            }
            val schemaResult = AiAnnotationValidator.parseSchema(batch[AiAnnotationBatchesTable.annotationSchemaSnapshot])
            if (schemaResult is ClassificationSchemaParseResult.Invalid) {
                return@transaction SubmitResultsOutcome.InvalidItem(schemaResult.message)
            }
            val schema = (schemaResult as ClassificationSchemaParseResult.Valid).schema
            val config = parseWorkerConfig(batch[AiAnnotationBatchesTable.config])
            val duplicateRequest = store.hasUploadRequest(batchId, request.requestId)
            val now = OffsetDateTime.now()
            var failedInRequest = 0

            request.items.forEach { input ->
                val ids = parseSubmissionIds(input)
                    ?: return@transaction SubmitResultsOutcome.InvalidItem("结果 ID、数据项 ID 或轮次无效")
                val row = store.findResultWithItem(batchId, ids.resultId)
                    ?: return@transaction SubmitResultsOutcome.InvalidItem("AI 结果不存在或不属于当前批次")
                if (row[AiAnnotationResultsTable.itemId] != ids.itemId ||
                    row[AiAnnotationResultsTable.roundNo] != input.roundNo
                ) {
                    return@transaction SubmitResultsOutcome.InvalidItem("结果与数据项或轮次不匹配")
                }

                val normalized = normalizeSubmission(input, schema, batchId, ids.itemId, config)
                    ?: return@transaction SubmitResultsOutcome.InvalidItem("AI 结果字段不完整或不符合标注结构")
                val currentStatus = row[AiAnnotationResultsTable.status]
                if (currentStatus != "processing") {
                    if (row[AiAnnotationResultsTable.resultHash] == normalized.hash) return@forEach
                    return@transaction SubmitResultsOutcome.Conflict("结果已经提交且内容不同")
                }
                if (row[DataItemsTable.status] != "ai_processing" ||
                    row[DataItemsTable.currentRoundNo] != input.roundNo
                ) {
                    return@transaction SubmitResultsOutcome.Conflict("数据项已不处于当前 AI 处理轮次")
                }

                when (normalized) {
                    is NormalizedSubmission.Failure -> {
                        failedInRequest++
                        store.markResultFailed(
                            resultId = ids.resultId,
                            resultHash = normalized.hash,
                            errorMessage = normalized.errorMessage,
                            rawOutput = normalized.rawOutput,
                            requestId = request.requestId,
                            chunkNo = request.chunkNo,
                            now = now,
                        )
                    }
                    is NormalizedSubmission.Success -> store.markResultSucceeded(
                        resultId = ids.resultId,
                        status = if (normalized.needsReview) "needs_review" else "ai_labeled",
                        result = normalized.result,
                        resultHash = normalized.hash,
                        confidence = normalized.confidence,
                        confidenceScore = normalized.confidenceScore,
                        reason = normalized.reason,
                        needsHumanReview = normalized.needsReview,
                        isSampled = normalized.isSampled,
                        riskFlags = objectMapper.writeValueAsString(normalized.riskFlags),
                        rawOutput = normalized.rawOutput,
                        requestId = request.requestId,
                        chunkNo = request.chunkNo,
                        now = now,
                    )
                }
            }

            if (!duplicateRequest) {
                store.addBatchUsage(
                    batchId,
                    request.modelRequestCount,
                    request.promptTokens,
                    request.completionTokens,
                    now,
                )
            }
            val counts = AiAnnotationBatchCounter.refresh(batchId, now)
            val batchStatus = if (counts.total > 0 && counts.processed == counts.total) {
                store.markBatchCompleted(batchId, now)
                "completed"
            } else {
                store.markBatchRunning(batchId, batch[AiAnnotationBatchesTable.startedAt] == null, now)
                "running"
            }
            SubmitResultsOutcome.Success(
                SubmitAiAnnotationResultsResponse(request.items.size, failedInRequest, batchStatus)
            )
        }

        return when (outcome) {
            SubmitResultsOutcome.NotFound -> Result.BadRequest("AI 标注批次不存在")
            SubmitResultsOutcome.InvalidBatchStatus -> Result.Conflict("当前批次状态不允许上传结果")
            is SubmitResultsOutcome.InvalidItem -> Result.BadRequest(outcome.message)
            is SubmitResultsOutcome.Conflict -> Result.Conflict(outcome.message)
            is SubmitResultsOutcome.Success -> Result.Success(outcome.value)
        }
    }

    fun failBatch(batchId: UUID, request: FailAiAnnotationBatchRequest): Result<MessageResponse> {
        val message = request.errorMessage.trim()
        if (message.isEmpty()) return Result.BadRequest("请提供批次失败原因")
        if (message.length > 2000) return Result.BadRequest("批次失败原因不能超过 2000 个字符")

        val found = transaction {
            val batch = store.findBatch(batchId) ?: return@transaction false
            if (batch[AiAnnotationBatchesTable.status] == "cancelled") return@transaction false
            val now = OffsetDateTime.now()
            store.failBatchAndRelease(batchId, message, now)
            AiAnnotationBatchCounter.refresh(batchId, now)
            true
        }
        return if (found) Result.Success(MessageResponse("批次已标记为失败，未处理数据已释放"))
        else Result.BadRequest("AI 标注批次不存在或状态不允许操作")
    }

    private fun normalizeSubmission(
        input: AiAnnotationResultInput,
        schema: ClassificationSchema,
        batchId: UUID,
        itemId: UUID,
        config: WorkerBatchConfig,
    ): NormalizedSubmission? {
        val rawOutput = input.rawOutput?.let(AiResultQuality::canonicalJson)
        if (rawOutput != null && rawOutput.length > 65_536) return null

        val errorMessage = input.errorMessage?.trim()?.takeIf(String::isNotEmpty)
        if (errorMessage != null) {
            if (errorMessage.length > 2000 || input.result != null) return null
            return NormalizedSubmission.Failure(
                hash = AiResultQuality.sha256("error:$errorMessage"),
                errorMessage = errorMessage,
                rawOutput = rawOutput,
            )
        }

        val result = input.result ?: return null
        if (AiAnnotationValidator.validateResult(result, schema) != null) return null
        val confidence = input.confidence?.trim() ?: return null
        if (confidence !in setOf("high", "medium", "low")) return null
        val confidenceScore = input.confidenceScore ?: return null
        if (confidenceScore !in 0.0..1.0) return null
        val reason = input.reason?.trim().orEmpty()

        val risk = AiResultRiskEvaluator.evaluate(
            result = result,
            confidence = confidence,
            confidenceScore = confidenceScore,
            reason = reason,
            modelRequestsReview = input.needsHumanReview,
            confidenceThreshold = config.confidenceThreshold,
            highRiskOptionValues = config.highRiskOptionValues,
            batchId = batchId,
            itemId = itemId,
            samplingRatio = config.samplingRatio,
        )
        val canonicalResult = AiResultQuality.canonicalJson(result)
        val hashSource = listOf(
            canonicalResult,
            confidence,
            confidenceScore.toString(),
            reason,
            input.needsHumanReview.toString(),
        ).joinToString("\n")
        return NormalizedSubmission.Success(
            hash = AiResultQuality.sha256(hashSource),
            result = canonicalResult,
            confidence = confidence,
            confidenceScore = BigDecimal.valueOf(confidenceScore),
            reason = reason,
            needsReview = risk.needsReview,
            isSampled = risk.isSampled,
            riskFlags = risk.flags,
            rawOutput = rawOutput,
        )
    }

    private fun parseSubmissionIds(input: AiAnnotationResultInput): SubmissionIds? {
        val resultId = runCatching { UUID.fromString(input.resultId) }.getOrNull() ?: return null
        val itemId = runCatching { UUID.fromString(input.itemId) }.getOrNull() ?: return null
        if (input.roundNo < 1) return null
        return SubmissionIds(resultId, itemId)
    }

    private fun validateSubmissionRequest(request: SubmitAiAnnotationResultsRequest): String? {
        return when {
            request.requestId.trim().isEmpty() || request.requestId.trim().length > 80 ->
                "requestId 不能为空且不能超过 80 个字符"
            request.chunkNo < 0 -> "chunkNo 不能小于 0"
            request.items.isEmpty() || request.items.size > 500 -> "每次必须上传 1 到 500 条结果"
            request.items.map { it.resultId }.distinct().size != request.items.size -> "同一次上传不能包含重复 resultId"
            request.modelRequestCount < 0 || request.promptTokens < 0 || request.completionTokens < 0 ->
                "模型调用和 token 用量不能为负数"
            else -> null
        }
    }

    private fun parseWorkerConfig(rawConfig: String): WorkerBatchConfig {
        val node = objectMapper.readTree(rawConfig)
        return WorkerBatchConfig(
            confidenceThreshold = node.path("confidenceThreshold").asDouble(0.85),
            samplingRatio = node.path("samplingRatio").asDouble(0.1),
            highRiskOptionValues = node.path("highRiskOptionValues")
                .takeIf(JsonNode::isArray)?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }?.toSet().orEmpty(),
            maxAttempts = node.path("maxAttempts").asInt(3).coerceIn(1, 10),
            metadataAllowList = node.path("metadataAllowList")
                .takeIf(JsonNode::isArray)?.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }?.toSet().orEmpty(),
        )
    }

    private fun filterMetadata(rawMetadata: String, allowList: Set<String>): JsonNode {
        if (allowList.isEmpty()) return objectMapper.createObjectNode()
        val source = runCatching { objectMapper.readTree(rawMetadata) }.getOrNull()
        if (source?.isObject != true) return objectMapper.createObjectNode()
        val filtered: ObjectNode = objectMapper.createObjectNode()
        allowList.forEach { key -> source.get(key)?.let { filtered.set<JsonNode>(key, it) } }
        return filtered
    }
}

private data class WorkerBatchConfig(
    val confidenceThreshold: Double,
    val samplingRatio: Double,
    val highRiskOptionValues: Set<String>,
    val maxAttempts: Int,
    val metadataAllowList: Set<String>,
)

private data class SubmissionIds(val resultId: UUID, val itemId: UUID)

private sealed class NormalizedSubmission(open val hash: String, open val rawOutput: String?) {
    data class Failure(
        override val hash: String,
        val errorMessage: String,
        override val rawOutput: String?,
    ) : NormalizedSubmission(hash, rawOutput)

    data class Success(
        override val hash: String,
        val result: String,
        val confidence: String,
        val confidenceScore: BigDecimal,
        val reason: String,
        val needsReview: Boolean,
        val isSampled: Boolean,
        val riskFlags: List<String>,
        override val rawOutput: String?,
    ) : NormalizedSubmission(hash, rawOutput)
}

private sealed class WorkerClaimOutcome {
    data object NotFound : WorkerClaimOutcome()
    data object InvalidStatus : WorkerClaimOutcome()
    data class InvalidContext(val message: String) : WorkerClaimOutcome()
    data class Success(val value: AiAnnotationWorkResponse) : WorkerClaimOutcome()
}

private sealed class SubmitResultsOutcome {
    data object NotFound : SubmitResultsOutcome()
    data object InvalidBatchStatus : SubmitResultsOutcome()
    data class InvalidItem(val message: String) : SubmitResultsOutcome()
    data class Conflict(val message: String) : SubmitResultsOutcome()
    data class Success(val value: SubmitAiAnnotationResultsResponse) : SubmitResultsOutcome()
}

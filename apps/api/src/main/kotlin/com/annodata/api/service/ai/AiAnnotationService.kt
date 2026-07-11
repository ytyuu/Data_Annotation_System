package com.annodata.api.service.ai

import com.annodata.api.db.AiAnnotationBatchesTable
import com.annodata.api.db.DatasetsTable
import com.annodata.api.http.Result
import com.annodata.api.models.AiAnnotationBatchListResponse
import com.annodata.api.models.AiAnnotationBatchResponse
import com.annodata.api.models.CreateAiAnnotationBatchRequest
import com.annodata.api.service.ai.store.AiAnnotationStore
import com.annodata.api.service.ai.validation.AiAnnotationValidator
import com.annodata.api.service.ai.validation.ClassificationSchemaParseResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class AiAnnotationService {
    private val store = AiAnnotationStore()
    private val objectMapper = ObjectMapper()

    fun createBatch(
        providerId: UUID,
        datasetId: UUID,
        request: CreateAiAnnotationBatchRequest,
    ): Result<AiAnnotationBatchResponse> {
        validateCreateRequest(request)?.let { return Result.BadRequest(it) }

        val normalizedHighRiskValues = request.highRiskOptionValues.map(String::trim).filter(String::isNotEmpty).distinct()
        val normalizedMetadataAllowList = request.metadataAllowList.map(String::trim).filter(String::isNotEmpty).distinct()

        val outcome = transactionWithSerializationRetry {
            val dataset = store.findProviderDataset(providerId, datasetId)
                ?: return@transactionWithSerializationRetry CreateBatchOutcome.NotFound
            if (dataset[DatasetsTable.status] !in setOf("in_progress", "reviewing")) {
                return@transactionWithSerializationRetry CreateBatchOutcome.InvalidStatus
            }

            val parsedSchema = AiAnnotationValidator.parseSchema(dataset[DatasetsTable.annotationSchema])
            if (parsedSchema is ClassificationSchemaParseResult.Invalid) {
                return@transactionWithSerializationRetry CreateBatchOutcome.InvalidSchema(parsedSchema.message)
            }
            val schema = (parsedSchema as ClassificationSchemaParseResult.Valid).schema
            val unknownHighRiskValues = normalizedHighRiskValues.filter { it !in schema.optionsByValue }
            if (unknownHighRiskValues.isNotEmpty()) {
                return@transactionWithSerializationRetry CreateBatchOutcome.InvalidHighRiskValues
            }

            val now = OffsetDateTime.now()
            val lockedItems = store.lockPendingTextItems(datasetId, request.maxItems, now)
            if (lockedItems.isEmpty()) return@transactionWithSerializationRetry CreateBatchOutcome.NoPendingItems

            val batchId = UUID.randomUUID()
            val config = objectMapper.writeValueAsString(
                mapOf(
                    "maxItems" to request.maxItems,
                    "confidenceThreshold" to request.confidenceThreshold,
                    "samplingRatio" to request.samplingRatio,
                    "highRiskOptionValues" to normalizedHighRiskValues,
                    "maxAttempts" to request.maxAttempts,
                    "metadataAllowList" to normalizedMetadataAllowList,
                )
            )
            store.insertBatch(
                batchId = batchId,
                datasetId = datasetId,
                providerId = providerId,
                modelName = request.modelName.trim(),
                promptVersion = request.promptVersion.trim(),
                schemaSnapshot = dataset[DatasetsTable.annotationSchema],
                guideSnapshot = dataset[DatasetsTable.annotationGuide],
                config = config,
                totalCount = lockedItems.size,
                now = now,
            )
            store.insertPendingResults(batchId, datasetId, lockedItems, now)
            val row = store.findProviderBatch(providerId, batchId)
                ?: error("AI batch was not found after insertion")
            CreateBatchOutcome.Success(toBatchResponse(row))
        }

        return when (outcome) {
            CreateBatchOutcome.NotFound -> Result.BadRequest("数据集不存在或无权访问")
            CreateBatchOutcome.InvalidStatus -> Result.BadRequest("仅可对进行中或审核中的数据集发起 AI 标注")
            is CreateBatchOutcome.InvalidSchema -> Result.BadRequest(outcome.message)
            CreateBatchOutcome.InvalidHighRiskValues -> Result.BadRequest("高风险选项包含数据集未定义的值")
            CreateBatchOutcome.NoPendingItems -> Result.Conflict("当前没有可供 AI 标注的待处理文本数据")
            is CreateBatchOutcome.Success -> Result.Success(outcome.value)
        }
    }

    fun listBatches(providerId: UUID, datasetId: UUID): Result<AiAnnotationBatchListResponse> {
        val outcome = transaction {
            if (store.findProviderDataset(providerId, datasetId) == null) return@transaction null
            store.listProviderBatches(providerId, datasetId).map(::toBatchResponse)
        } ?: return Result.BadRequest("数据集不存在或无权访问")

        return Result.Success(AiAnnotationBatchListResponse(outcome))
    }

    fun getBatch(providerId: UUID, batchId: UUID): Result<AiAnnotationBatchResponse> {
        val row = transaction { store.findProviderBatch(providerId, batchId) }
            ?: return Result.BadRequest("AI 标注批次不存在或无权访问")
        return Result.Success(toBatchResponse(row))
    }

    private fun validateCreateRequest(request: CreateAiAnnotationBatchRequest): String? {
        return when {
            request.maxItems !in 1..5000 -> "批次数据量必须在 1 到 5000 之间"
            request.modelName.trim() !in setOf("deepseek-v4-flash", "deepseek-v4-pro") ->
                "模型仅支持 deepseek-v4-flash 或 deepseek-v4-pro"
            request.promptVersion.trim().isEmpty() || request.promptVersion.trim().length > 64 ->
                "Prompt 版本不能为空且不能超过 64 个字符"
            request.confidenceThreshold !in 0.0..1.0 -> "置信度阈值必须在 0 到 1 之间"
            request.samplingRatio !in 0.0..1.0 -> "抽检比例必须在 0 到 1 之间"
            request.maxAttempts !in 1..10 -> "最大尝试次数必须在 1 到 10 之间"
            request.metadataAllowList.any { it.trim().length !in 1..64 } -> "metadata 白名单字段长度必须在 1 到 64 之间"
            else -> null
        }
    }

    private fun toBatchResponse(row: ResultRow): AiAnnotationBatchResponse {
        return AiAnnotationBatchResponse(
            id = row[AiAnnotationBatchesTable.id].toString(),
            datasetId = row[AiAnnotationBatchesTable.datasetId].toString(),
            datasetName = row[DatasetsTable.name],
            providerId = row[AiAnnotationBatchesTable.providerId].toString(),
            status = row[AiAnnotationBatchesTable.status],
            modelProvider = row[AiAnnotationBatchesTable.modelProvider],
            modelName = row[AiAnnotationBatchesTable.modelName],
            promptVersion = row[AiAnnotationBatchesTable.promptVersion],
            config = objectMapper.readTree(row[AiAnnotationBatchesTable.config]),
            totalCount = row[AiAnnotationBatchesTable.totalCount],
            processedCount = row[AiAnnotationBatchesTable.processedCount],
            successCount = row[AiAnnotationBatchesTable.successCount],
            failedCount = row[AiAnnotationBatchesTable.failedCount],
            needsReviewCount = row[AiAnnotationBatchesTable.needsReviewCount],
            acceptedCount = row[AiAnnotationBatchesTable.acceptedCount],
            rejectedCount = row[AiAnnotationBatchesTable.rejectedCount],
            modelRequestCount = row[AiAnnotationBatchesTable.modelRequestCount],
            promptTokens = row[AiAnnotationBatchesTable.promptTokens],
            completionTokens = row[AiAnnotationBatchesTable.completionTokens],
            errorMessage = row[AiAnnotationBatchesTable.errorMessage],
            startedAt = row[AiAnnotationBatchesTable.startedAt]?.toString(),
            finishedAt = row[AiAnnotationBatchesTable.finishedAt]?.toString(),
            createdAt = row[AiAnnotationBatchesTable.createdAt].toString(),
            updatedAt = row[AiAnnotationBatchesTable.updatedAt].toString(),
        )
    }

    private fun <T> transactionWithSerializationRetry(block: () -> T): T {
        var lastFailure: ExposedSQLException? = null
        repeat(3) { attempt ->
            try {
                return transaction { block() }
            } catch (error: ExposedSQLException) {
                if (error.sqlState != "40001" || attempt == 2) throw error
                lastFailure = error
            }
        }
        throw checkNotNull(lastFailure)
    }
}

private sealed class CreateBatchOutcome {
    data object NotFound : CreateBatchOutcome()
    data object InvalidStatus : CreateBatchOutcome()
    data class InvalidSchema(val message: String) : CreateBatchOutcome()
    data object InvalidHighRiskValues : CreateBatchOutcome()
    data object NoPendingItems : CreateBatchOutcome()
    data class Success(val value: AiAnnotationBatchResponse) : CreateBatchOutcome()
}

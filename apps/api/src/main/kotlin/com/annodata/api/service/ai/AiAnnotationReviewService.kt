package com.annodata.api.service.ai

import com.annodata.api.db.AiAnnotationBatchesTable
import com.annodata.api.db.AiAnnotationResultsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.http.Result
import com.annodata.api.models.AiAnnotationResultListResponse
import com.annodata.api.models.AiAnnotationResultResponse
import com.annodata.api.models.BatchReviewAiAnnotationResultsRequest
import com.annodata.api.models.ReviewAiAnnotationResultRequest
import com.annodata.api.models.ReviewAiAnnotationResultResponse
import com.annodata.api.service.ai.store.AiAnnotationBatchCounter
import com.annodata.api.service.ai.store.AiAnnotationStore
import com.annodata.api.service.ai.validation.AiAnnotationValidator
import com.annodata.api.service.ai.validation.AiReviewActionPolicy
import com.annodata.api.service.ai.validation.AiResultQuality
import com.annodata.api.service.ai.validation.ClassificationSchemaParseResult
import com.annodata.api.service.dataset.DatasetQueryHelper
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class AiAnnotationReviewService {
    private val store = AiAnnotationStore()
    private val objectMapper = ObjectMapper()
    private val resultStatuses = setOf(
        "pending", "processing", "ai_labeled", "needs_review", "accepted", "rejected", "failed"
    )

    fun listResults(
        providerId: UUID,
        batchId: UUID,
        status: String?,
        reviewMode: String?,
        page: Int,
        pageSize: Int,
    ): Result<AiAnnotationResultListResponse> {
        if (status != null && status !in resultStatuses) return Result.BadRequest("结果状态筛选值无效")
        if (reviewMode != null && reviewMode !in setOf("sampling", "mandatory")) {
            return Result.BadRequest("reviewMode 仅支持 sampling 或 mandatory")
        }
        if (page < 1 || pageSize !in 1..200) return Result.BadRequest("分页参数无效")

        val data = transaction {
            if (store.findProviderBatch(providerId, batchId) == null) return@transaction null
            store.listProviderResults(providerId, batchId, status, reviewMode, page, pageSize)
        } ?: return Result.BadRequest("AI 标注批次不存在或无权访问")

        return Result.Success(
            AiAnnotationResultListResponse(
                items = data.first.map(::toResultResponse),
                total = data.second,
                page = page,
                pageSize = pageSize,
            )
        )
    }

    fun reviewResult(
        providerId: UUID,
        resultId: UUID,
        request: ReviewAiAnnotationResultRequest,
    ): Result<ReviewAiAnnotationResultResponse> {
        val action = request.action.trim()
        if (action !in setOf("accept", "modify_accept", "reject_to_human", "reject_retry")) {
            return Result.BadRequest("审核动作无效")
        }
        val comment = request.comment?.trim()?.takeIf(String::isNotEmpty)
        if (comment != null && comment.length > 2000) return Result.BadRequest("审核意见不能超过 2000 个字符")

        val outcome = transaction {
            val row = store.findProviderResult(providerId, resultId)
                ?: return@transaction ReviewOutcome.NotFound
            val currentStatus = row[AiAnnotationResultsTable.status]
            if (!AiReviewActionPolicy.isAllowed(currentStatus, action)) {
                return@transaction ReviewOutcome.InvalidStatus
            }
            if (row[DataItemsTable.status] != "ai_processing") return@transaction ReviewOutcome.InvalidItemStatus

            val now = OffsetDateTime.now()
            val batchId = row[AiAnnotationResultsTable.batchId]
            val datasetId = row[AiAnnotationResultsTable.datasetId]
            when (action) {
                "accept", "modify_accept" -> {
                    val finalResult = if (action == "modify_accept") {
                        val modified = request.acceptedResult ?: return@transaction ReviewOutcome.InvalidResult("修改接受时必须提交结果")
                        val schemaResult = AiAnnotationValidator.parseSchema(
                            row[AiAnnotationBatchesTable.annotationSchemaSnapshot]
                        )
                        if (schemaResult is ClassificationSchemaParseResult.Invalid) {
                            return@transaction ReviewOutcome.InvalidResult(schemaResult.message)
                        }
                        val schema = (schemaResult as ClassificationSchemaParseResult.Valid).schema
                        AiAnnotationValidator.validateResult(modified, schema)?.let {
                            return@transaction ReviewOutcome.InvalidResult(it)
                        }
                        AiResultQuality.canonicalJson(modified)
                    } else {
                        row[AiAnnotationResultsTable.result]
                            ?: return@transaction ReviewOutcome.InvalidResult("AI 结果为空，无法接受")
                    }
                    store.markResultAccepted(
                        resultId,
                        providerId,
                        if (action == "modify_accept") finalResult else null,
                        action,
                        comment,
                        now,
                    )
                    store.markDataItemAcceptedFromAi(row[AiAnnotationResultsTable.itemId], providerId, finalResult, now)
                    DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
                    DatasetQueryHelper.checkAndTransitionToReviewing(datasetId, now)
                }
                "reject_to_human" -> {
                    store.markResultRejectedToHuman(resultId, providerId, comment, now)
                    store.releaseDataItemToHuman(row[AiAnnotationResultsTable.itemId], now)
                }
                "reject_retry" -> {
                    store.resetResultForRetry(resultId, providerId, comment, now)
                    store.markBatchRunning(batchId, row[AiAnnotationBatchesTable.startedAt] == null, now)
                }
            }
            AiAnnotationBatchCounter.refresh(batchId, now)
            ReviewOutcome.Success
        }

        return when (outcome) {
            ReviewOutcome.NotFound -> Result.BadRequest("AI 标注结果不存在或无权访问")
            ReviewOutcome.InvalidStatus -> Result.Conflict("当前结果状态不允许执行该审核动作")
            ReviewOutcome.InvalidItemStatus -> Result.Conflict("数据项已不处于 AI 处理状态")
            is ReviewOutcome.InvalidResult -> Result.BadRequest(outcome.message)
            ReviewOutcome.Success -> Result.Success(ReviewAiAnnotationResultResponse("审核结果已保存", 1))
        }
    }

    fun batchReview(
        providerId: UUID,
        request: BatchReviewAiAnnotationResultsRequest,
    ): Result<ReviewAiAnnotationResultResponse> {
        if (request.action != "accept") return Result.BadRequest("第一版批量操作仅支持 accept")
        if (request.resultIds.isEmpty() || request.resultIds.size > 500) {
            return Result.BadRequest("每次批量接受必须选择 1 到 500 条结果")
        }
        val batchId = runCatching { UUID.fromString(request.batchId) }.getOrNull()
            ?: return Result.BadRequest("batchId 无效")
        val resultIds = request.resultIds.map { runCatching { UUID.fromString(it) }.getOrNull() }
        if (resultIds.any { it == null } || resultIds.filterNotNull().distinct().size != resultIds.size) {
            return Result.BadRequest("resultIds 包含无效或重复 ID")
        }
        val comment = request.comment?.trim()?.takeIf(String::isNotEmpty)
        if (comment != null && comment.length > 2000) return Result.BadRequest("审核意见不能超过 2000 个字符")

        val outcome = transaction {
            val batch = store.findProviderBatch(providerId, batchId)
                ?: return@transaction BatchReviewOutcome.NotFound
            if (store.hasUnreviewedSample(batchId)) return@transaction BatchReviewOutcome.UnreviewedSample
            val now = OffsetDateTime.now()
            resultIds.filterNotNull().forEach { resultId ->
                val row = store.findProviderResult(providerId, resultId)
                    ?: return@transaction BatchReviewOutcome.InvalidResults
                if (row[AiAnnotationResultsTable.batchId] != batchId ||
                    row[AiAnnotationResultsTable.status] != "ai_labeled" ||
                    row[AiAnnotationResultsTable.isSampled] ||
                    row[DataItemsTable.status] != "ai_processing"
                ) {
                    return@transaction BatchReviewOutcome.InvalidResults
                }
                val finalResult = row[AiAnnotationResultsTable.result]
                    ?: return@transaction BatchReviewOutcome.InvalidResults
                store.markResultAccepted(resultId, providerId, null, "accept", comment, now)
                store.markDataItemAcceptedFromAi(row[AiAnnotationResultsTable.itemId], providerId, finalResult, now)
            }
            AiAnnotationBatchCounter.refresh(batchId, now)
            val datasetId = batch[AiAnnotationBatchesTable.datasetId]
            DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
            DatasetQueryHelper.checkAndTransitionToReviewing(datasetId, now)
            BatchReviewOutcome.Success(resultIds.size)
        }

        return when (outcome) {
            BatchReviewOutcome.NotFound -> Result.BadRequest("AI 标注批次不存在或无权访问")
            BatchReviewOutcome.UnreviewedSample -> Result.Conflict("仍有抽检样本未处理，不能批量接受低风险结果")
            BatchReviewOutcome.InvalidResults -> Result.Conflict("批量结果包含不可接受的记录")
            is BatchReviewOutcome.Success -> Result.Success(
                ReviewAiAnnotationResultResponse("已批量接受 ${outcome.count} 条结果", outcome.count)
            )
        }
    }

    private fun toResultResponse(row: ResultRow): AiAnnotationResultResponse {
        return AiAnnotationResultResponse(
            id = row[AiAnnotationResultsTable.id].toString(),
            batchId = row[AiAnnotationResultsTable.batchId].toString(),
            datasetId = row[AiAnnotationResultsTable.datasetId].toString(),
            itemId = row[AiAnnotationResultsTable.itemId].toString(),
            roundNo = row[AiAnnotationResultsTable.roundNo],
            status = row[AiAnnotationResultsTable.status],
            content = row[DataItemsTable.content],
            contentType = row[DataItemsTable.contentType],
            metadata = objectMapper.readTree(row[DataItemsTable.metadata]),
            result = row[AiAnnotationResultsTable.result]?.let(objectMapper::readTree),
            acceptedResult = row[AiAnnotationResultsTable.acceptedResult]?.let(objectMapper::readTree),
            confidence = row[AiAnnotationResultsTable.confidence],
            confidenceScore = row[AiAnnotationResultsTable.confidenceScore]?.toPlainString(),
            reason = row[AiAnnotationResultsTable.reason],
            needsHumanReview = row[AiAnnotationResultsTable.needsHumanReview],
            isSampled = row[AiAnnotationResultsTable.isSampled],
            riskFlags = objectMapper.readTree(row[AiAnnotationResultsTable.riskFlags]),
            errorMessage = row[AiAnnotationResultsTable.errorMessage],
            attemptCount = row[AiAnnotationResultsTable.attemptCount],
            reviewedBy = row[AiAnnotationResultsTable.reviewedBy]?.toString(),
            reviewedAt = row[AiAnnotationResultsTable.reviewedAt]?.toString(),
            reviewAction = row[AiAnnotationResultsTable.reviewAction],
            reviewComment = row[AiAnnotationResultsTable.reviewComment],
            createdAt = row[AiAnnotationResultsTable.createdAt].toString(),
            updatedAt = row[AiAnnotationResultsTable.updatedAt].toString(),
        )
    }
}

private sealed class ReviewOutcome {
    data object NotFound : ReviewOutcome()
    data object InvalidStatus : ReviewOutcome()
    data object InvalidItemStatus : ReviewOutcome()
    data class InvalidResult(val message: String) : ReviewOutcome()
    data object Success : ReviewOutcome()
}

private sealed class BatchReviewOutcome {
    data object NotFound : BatchReviewOutcome()
    data object UnreviewedSample : BatchReviewOutcome()
    data object InvalidResults : BatchReviewOutcome()
    data class Success(val count: Int) : BatchReviewOutcome()
}

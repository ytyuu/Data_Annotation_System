package com.annodata.api.service.ai.validation

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

data class AiResultRiskAssessment(
    val needsReview: Boolean,
    val isSampled: Boolean,
    val flags: List<String>,
)

object AiResultRiskEvaluator {
    fun evaluate(
        result: JsonNode,
        confidence: String,
        confidenceScore: Double,
        reason: String,
        modelRequestsReview: Boolean,
        confidenceThreshold: Double,
        highRiskOptionValues: Set<String>,
        batchId: UUID,
        itemId: UUID,
        samplingRatio: Double,
    ): AiResultRiskAssessment {
        val flags = buildList {
            if (modelRequestsReview) add("model_requested_review")
            if (confidence == "low") add("low_confidence_level")
            if (confidenceScore < confidenceThreshold) add("below_confidence_threshold")
            if (reason.isBlank()) add("missing_reason")
            if (AiResultQuality.selectedMainValues(result).any { it in highRiskOptionValues }) {
                add("high_risk_option")
            }
        }
        val needsReview = flags.isNotEmpty()
        return AiResultRiskAssessment(
            needsReview = needsReview,
            isSampled = !needsReview && AiResultQuality.isSampled(batchId, itemId, samplingRatio),
            flags = flags,
        )
    }
}

object AiReviewActionPolicy {
    private val reviewableStatuses = setOf("ai_labeled", "needs_review", "failed")
    private val acceptanceActions = setOf("accept", "modify_accept")

    fun isAllowed(status: String, action: String): Boolean {
        if (status !in reviewableStatuses) return false
        if (status == "failed" && action in acceptanceActions) return false
        return action in setOf("accept", "modify_accept", "reject_to_human", "reject_retry")
    }
}

package com.annodata.api.service.ai.validation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AiResultRiskEvaluatorTest {
    private val objectMapper = ObjectMapper()
    private val batchId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val itemId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `flags model request low confidence missing reason and high risk option`() {
        val assessment = AiResultRiskEvaluator.evaluate(
            result = objectMapper.readTree("""{"value":"risk"}"""),
            confidence = "low",
            confidenceScore = 0.5,
            reason = " ",
            modelRequestsReview = true,
            confidenceThreshold = 0.85,
            highRiskOptionValues = setOf("risk"),
            batchId = batchId,
            itemId = itemId,
            samplingRatio = 1.0,
        )

        assertTrue(assessment.needsReview)
        assertFalse(assessment.isSampled)
        assertEquals(
            listOf(
                "model_requested_review",
                "low_confidence_level",
                "below_confidence_threshold",
                "missing_reason",
                "high_risk_option",
            ),
            assessment.flags,
        )
    }

    @Test
    fun `samples only clean low risk results`() {
        val assessment = AiResultRiskEvaluator.evaluate(
            result = objectMapper.readTree("""{"value":"safe"}"""),
            confidence = "high",
            confidenceScore = 0.95,
            reason = "符合安全规则",
            modelRequestsReview = false,
            confidenceThreshold = 0.85,
            highRiskOptionValues = setOf("risk"),
            batchId = batchId,
            itemId = itemId,
            samplingRatio = 1.0,
        )

        assertFalse(assessment.needsReview)
        assertTrue(assessment.isSampled)
        assertTrue(assessment.flags.isEmpty())
    }

    @Test
    fun `review policy enforces legal status transitions`() {
        assertTrue(AiReviewActionPolicy.isAllowed("ai_labeled", "accept"))
        assertTrue(AiReviewActionPolicy.isAllowed("needs_review", "modify_accept"))
        assertTrue(AiReviewActionPolicy.isAllowed("failed", "reject_retry"))
        assertFalse(AiReviewActionPolicy.isAllowed("failed", "accept"))
        assertFalse(AiReviewActionPolicy.isAllowed("accepted", "reject_to_human"))
        assertFalse(AiReviewActionPolicy.isAllowed("pending", "reject_retry"))
    }
}

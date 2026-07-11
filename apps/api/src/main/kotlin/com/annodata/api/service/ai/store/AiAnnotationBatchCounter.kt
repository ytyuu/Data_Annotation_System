package com.annodata.api.service.ai.store

import com.annodata.api.db.AiAnnotationBatchesTable
import com.annodata.api.db.AiAnnotationResultsTable
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

data class AiAnnotationBatchCounts(
    val total: Int,
    val processed: Int,
    val success: Int,
    val failed: Int,
    val needsReview: Int,
    val accepted: Int,
    val rejected: Int,
)

internal object AiAnnotationBatchCounter {
    fun refresh(batchId: UUID, now: OffsetDateTime = OffsetDateTime.now()): AiAnnotationBatchCounts {
        val countExpression = AiAnnotationResultsTable.id.count()
        val countsByStatus = AiAnnotationResultsTable
            .select(AiAnnotationResultsTable.status, countExpression)
            .where { AiAnnotationResultsTable.batchId eq batchId }
            .groupBy(AiAnnotationResultsTable.status)
            .associate { row -> row[AiAnnotationResultsTable.status] to row[countExpression].toInt() }

        val total = countsByStatus.values.sum()
        val pending = countsByStatus["pending"] ?: 0
        val processing = countsByStatus["processing"] ?: 0
        val failed = countsByStatus["failed"] ?: 0
        val needsReview = countsByStatus["needs_review"] ?: 0
        val accepted = countsByStatus["accepted"] ?: 0
        val rejected = countsByStatus["rejected"] ?: 0
        val processed = total - pending - processing
        val success = AiAnnotationResultsTable
            .selectAll()
            .where {
                (AiAnnotationResultsTable.batchId eq batchId) and
                    AiAnnotationResultsTable.result.isNotNull() and
                    (AiAnnotationResultsTable.status neq "failed")
            }
            .count()
            .toInt()

        AiAnnotationBatchesTable.update({ AiAnnotationBatchesTable.id eq batchId }) {
            it[totalCount] = total
            it[processedCount] = processed
            it[successCount] = success
            it[failedCount] = failed
            it[needsReviewCount] = needsReview
            it[acceptedCount] = accepted
            it[rejectedCount] = rejected
            it[updatedAt] = now
        }

        return AiAnnotationBatchCounts(total, processed, success, failed, needsReview, accepted, rejected)
    }
}

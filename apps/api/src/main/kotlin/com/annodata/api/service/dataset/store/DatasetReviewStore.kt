package com.annodata.api.service.dataset.store

import com.annodata.api.db.DatasetReviewsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

internal class DatasetReviewStore {
    fun findDatasetReviewRow(datasetId: UUID, providerId: UUID): ResultRow? =
        DatasetReviewsTable
            .selectAll()
            .where {
                (DatasetReviewsTable.datasetId eq datasetId) and
                    (DatasetReviewsTable.providerId eq providerId)
            }
            .limit(1)
            .firstOrNull()

    fun upsertDatasetReview(
        datasetId: UUID,
        providerId: UUID,
        reviewStatus: String,
        sampledCount: Int,
        disputedItemCount: Int,
        opinion: String?,
        now: OffsetDateTime,
    ) {
        val existingReview = findDatasetReviewRow(datasetId, providerId)
        if (existingReview != null) {
            DatasetReviewsTable.update({ DatasetReviewsTable.id eq existingReview[DatasetReviewsTable.id] }) {
                it[DatasetReviewsTable.status] = reviewStatus
                it[DatasetReviewsTable.sampledItemCount] = sampledCount
                it[DatasetReviewsTable.disputedItemCount] = disputedItemCount
                it[DatasetReviewsTable.opinion] = opinion
                it[DatasetReviewsTable.reviewedAt] = now
                it[updatedAt] = now
            }
        } else {
            DatasetReviewsTable.insert {
                it[id] = UUID.randomUUID()
                it[DatasetReviewsTable.datasetId] = datasetId
                it[DatasetReviewsTable.providerId] = providerId
                it[DatasetReviewsTable.status] = reviewStatus
                it[DatasetReviewsTable.sampledItemCount] = sampledCount
                it[DatasetReviewsTable.disputedItemCount] = disputedItemCount
                it[DatasetReviewsTable.opinion] = opinion
                it[DatasetReviewsTable.reviewedAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    fun updateDatasetReviewStatus(reviewId: UUID, reviewStatus: String, now: OffsetDateTime) {
        DatasetReviewsTable.update({ DatasetReviewsTable.id eq reviewId }) {
            it[DatasetReviewsTable.status] = reviewStatus
            it[DatasetReviewsTable.reviewedAt] = now
            it[updatedAt] = now
        }
    }
}

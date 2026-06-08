package com.example.api.service.dataset.policy

import com.example.api.service.dataset.DatasetQueryHelper

internal object DatasetStatusPolicy {
    private val claimableDatasetStatuses = setOf("in_progress", "reviewing")
    private val reviewableItemStatuses = setOf("annotated", "accepted")

    fun canClaimDataset(status: String): Boolean = status in claimableDatasetStatuses

    fun canReviewDataset(status: String): Boolean = status == "reviewing"

    fun canReviewItem(status: String): Boolean = status in reviewableItemStatuses

    fun canRepublishDataset(status: String): Boolean = status == "reviewing"

    fun canCompleteReview(status: String): Boolean = status == "reviewing"
}

internal object ClaimPolicy {
    private const val MAX_ACTIVE_TASK_COUNT = 5

    fun hasReachedActiveTaskLimit(totalActive: Int): Boolean = totalActive >= MAX_ACTIVE_TASK_COUNT

    fun canClaimAnnotation(hasPending: Boolean, hasActiveInDataset: Boolean, totalActive: Int): Boolean =
        hasPending && !hasActiveInDataset && !hasReachedActiveTaskLimit(totalActive)

    fun canClaimReview(hasReviewable: Boolean, hasActiveReviewInDataset: Boolean, totalActive: Int): Boolean =
        hasReviewable && !hasActiveReviewInDataset && !hasReachedActiveTaskLimit(totalActive)
}

internal object AnnotationReviewPolicy {
    fun resolveSubmittedAnnotationDispute(
        isReviewBatch: Boolean,
        submissionMarkedDisputed: Boolean,
        originalAnnotationMarkedDisputed: Boolean,
        originalResult: String?,
        submittedResult: String,
    ): Boolean {
        if (!isReviewBatch || originalResult == null) {
            return submissionMarkedDisputed
        }

        return originalAnnotationMarkedDisputed ||
            submissionMarkedDisputed ||
            !DatasetQueryHelper.areAnnotationResultsConsistent(originalResult, submittedResult)
    }

    fun shouldFinalizeAsDisputed(
        originalMarkedDisputed: Boolean,
        reviewMarkedDisputed: Boolean,
        originalResult: String,
        reviewResult: String,
    ): Boolean =
        originalMarkedDisputed ||
            reviewMarkedDisputed ||
            !DatasetQueryHelper.areAnnotationResultsConsistent(originalResult, reviewResult)
}

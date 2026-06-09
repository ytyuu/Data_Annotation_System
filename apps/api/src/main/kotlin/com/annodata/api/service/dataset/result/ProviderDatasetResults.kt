package com.annodata.api.service.dataset.result

import com.annodata.api.models.DisputedItemDetailResponse
import com.annodata.api.models.FinishReviewResponse
import com.annodata.api.models.ImportDataItemsResponse
import com.annodata.api.models.ReviewDetailResponse
import java.util.UUID

internal sealed class DisputeDetailResult {
    data class Success(val value: DisputedItemDetailResponse) : DisputeDetailResult()
    data object NotFound : DisputeDetailResult()
    data object InvalidStatus : DisputeDetailResult()
}

internal sealed class ImportDataItemsTransactionResult {
    data class Success(val value: ImportDataItemsResponse) : ImportDataItemsTransactionResult()
    data object InvalidStatus : ImportDataItemsTransactionResult()
}

internal enum class DeleteDatasetTransactionResult {
    Success,
    NotFound,
    InvalidStatus,
}

internal enum class UpdateDatasetTransactionResult {
    Success,
    NotFound,
    InvalidStatus,
}

internal enum class PublishDatasetTransactionResult {
    Success,
    NotFound,
    InvalidStatus,
    EmptyDataset,
}

internal enum class ResolveDisputeTransactionResult {
    Success,
    NotFound,
    InvalidStatus,
    InvalidAnnotations,
}

internal sealed class DeleteDataItemTransactionResult {
    data class Success(val itemCount: Int) : DeleteDataItemTransactionResult()
    data object NotFound : DeleteDataItemTransactionResult()
    data object InvalidStatus : DeleteDataItemTransactionResult()
}

internal sealed class RepublishRejectedItemsResult {
    data class Success(val count: Int) : RepublishRejectedItemsResult()
    data object NotFound : RepublishRejectedItemsResult()
    data object InvalidStatus : RepublishRejectedItemsResult()
    data object NoRejectedItems : RepublishRejectedItemsResult()
}

internal sealed class ReviewItemsResult {
    data class Success(val value: ReviewDetailResponse) : ReviewItemsResult()
    data object NotFound : ReviewItemsResult()
    data object InvalidStatus : ReviewItemsResult()
}

internal sealed class SubmitReviewTransactionResult {
    data class Success(val datasetStatus: String) : SubmitReviewTransactionResult()
    data object NotFound : SubmitReviewTransactionResult()
    data object InvalidStatus : SubmitReviewTransactionResult()
}

internal sealed class FinishReviewTransactionResult {
    data class Success(
        val datasetStatus: String,
        val acceptedCount: Int,
        val rejectedCount: Int,
    ) : FinishReviewTransactionResult()

    data object NotFound : FinishReviewTransactionResult()
    data object InvalidStatus : FinishReviewTransactionResult()
    data object NotAllReviewed : FinishReviewTransactionResult()
}

internal sealed class ReviewItemTransactionResult {
    data object Success : ReviewItemTransactionResult()
    data object NotFound : ReviewItemTransactionResult()
    data object InvalidStatus : ReviewItemTransactionResult()
}

internal sealed class CompleteReviewTransactionResult {
    data object Success : CompleteReviewTransactionResult()
    data object NotFound : CompleteReviewTransactionResult()
    data object InvalidStatus : CompleteReviewTransactionResult()
    data object HasUnfinishedItems : CompleteReviewTransactionResult()
}

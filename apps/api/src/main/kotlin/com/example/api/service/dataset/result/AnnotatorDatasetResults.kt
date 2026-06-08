package com.example.api.service.dataset.result

import com.example.api.models.ClaimTasksResponse

internal sealed class ClaimTasksTransactionResult {
    data class Success(val value: ClaimTasksResponse) : ClaimTasksTransactionResult()
    data object NotFound : ClaimTasksTransactionResult()
    data object InvalidStatus : ClaimTasksTransactionResult()
    data object AlreadyClaimed : ClaimTasksTransactionResult()
    data object TooManyActive : ClaimTasksTransactionResult()
    data object EmptyDataset : ClaimTasksTransactionResult()
}

internal sealed class ReturnTaskTransactionResult {
    data object Success : ReturnTaskTransactionResult()
    data object NotFound : ReturnTaskTransactionResult()
    data object InvalidStatus : ReturnTaskTransactionResult()
}

internal sealed class StartTaskBatchTransactionResult {
    data object Success : StartTaskBatchTransactionResult()
    data object NotFound : StartTaskBatchTransactionResult()
    data object InvalidStatus : StartTaskBatchTransactionResult()
}

internal sealed class SubmitAnnotationBatchResult {
    data object NotFound : SubmitAnnotationBatchResult()
    data object InvalidStatus : SubmitAnnotationBatchResult()
    data object InvalidTasks : SubmitAnnotationBatchResult()
    data class Success(val submittedCount: Int, val totalCount: Int) : SubmitAnnotationBatchResult()
}

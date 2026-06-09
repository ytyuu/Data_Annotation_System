package com.annodata.api.service.dataset.store

import java.time.OffsetDateTime
import java.util.UUID

internal data class ClaimedDataItem(
    val id: UUID,
    val datasetId: UUID,
    val content: String,
    val contentType: String,
    val metadata: String,
    val currentRoundNo: Int,
    val finalResult: String?,
    val finalizedAt: OffsetDateTime?,
    val finalizedBy: UUID?,
    val status: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

internal data class TaskStatusCounts(
    val assigned: Int = 0,
    val inProgress: Int = 0,
    val submitted: Int = 0,
)

internal data class ProviderDatasetRecord(
    val id: UUID,
    val status: String,
)

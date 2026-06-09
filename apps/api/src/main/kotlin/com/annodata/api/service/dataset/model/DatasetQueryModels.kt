package com.annodata.api.service.dataset.model

import org.jetbrains.exposed.sql.ResultRow
import java.util.UUID

internal data class BatchTaskRows(
    val taskRows: List<ResultRow>,
    val annotationsByTask: Map<UUID, ResultRow>,
    val items: Map<UUID, ResultRow>,
)

internal data class ParsedSubmission(
    val taskId: UUID,
    val itemId: UUID,
    val result: String,
    val isDisputed: Boolean,
    val comment: String?,
)

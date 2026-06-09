package com.annodata.api.service.dataset.view

import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.DatasetsTable
import com.annodata.api.models.DataItemResponse
import com.annodata.api.models.DatasetResponse
import org.jetbrains.exposed.sql.ResultRow

internal fun toDataItemResponse(row: ResultRow): DataItemResponse {
    return DataItemResponse(
        id = row[DataItemsTable.id].toString(),
        datasetId = row[DataItemsTable.datasetId].toString(),
        content = row[DataItemsTable.content],
        contentType = row[DataItemsTable.contentType],
        metadata = row[DataItemsTable.metadata],
        currentRoundNo = row[DataItemsTable.currentRoundNo],
        finalResult = row[DataItemsTable.finalResult],
        finalizedAt = row[DataItemsTable.finalizedAt]?.toString(),
        finalizedBy = row[DataItemsTable.finalizedBy]?.toString(),
        status = row[DataItemsTable.status],
        createdAt = row[DataItemsTable.createdAt].toString(),
        updatedAt = row[DataItemsTable.updatedAt].toString(),
    )
}

internal fun toDatasetResponse(
    row: ResultRow,
    canClaim: Boolean? = null,
    pendingItemCount: Int? = null,
    reviewableItemCount: Int? = null,
    completedItemCount: Int? = null,
    disputedItemCount: Int? = null,
): DatasetResponse {
    return DatasetResponse(
        id = row[DatasetsTable.id].toString(),
        providerId = row[DatasetsTable.providerId].toString(),
        name = row[DatasetsTable.name],
        description = row[DatasetsTable.description],
        annotationGuide = row[DatasetsTable.annotationGuide],
        annotationSchema = row[DatasetsTable.annotationSchema],
        status = row[DatasetsTable.status],
        targetCompletionRatio = row[DatasetsTable.targetCompletionRatio].toPlainString(),
        itemCount = row[DatasetsTable.itemCount],
        completedItemCount = completedItemCount ?: row[DatasetsTable.completedItemCount],
        createdAt = row[DatasetsTable.createdAt].toString(),
        updatedAt = row[DatasetsTable.updatedAt].toString(),
        canClaim = canClaim,
        pendingItemCount = pendingItemCount,
        reviewableItemCount = reviewableItemCount,
        disputedItemCount = disputedItemCount,
        hasBeenReviewed = row[DatasetsTable.status] == "completed",
    )
}

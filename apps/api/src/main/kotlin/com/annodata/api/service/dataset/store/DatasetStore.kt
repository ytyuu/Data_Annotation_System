package com.annodata.api.service.dataset.store

import com.annodata.api.db.DatasetsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

internal class DatasetStore {
    fun insertDraftDataset(
        providerId: UUID,
        datasetId: UUID,
        name: String,
        description: String?,
        annotationGuide: String?,
        annotationSchema: String,
        targetCompletionRatio: BigDecimal,
        now: OffsetDateTime,
    ) {
        DatasetsTable.insert {
            it[id] = datasetId
            it[DatasetsTable.providerId] = providerId
            it[DatasetsTable.name] = name
            it[DatasetsTable.description] = description
            it[DatasetsTable.annotationGuide] = annotationGuide
            it[DatasetsTable.annotationSchema] = annotationSchema
            it[status] = "draft"
            it[DatasetsTable.targetCompletionRatio] = targetCompletionRatio
            it[itemCount] = 0
            it[completedItemCount] = 0
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    fun listProviderDatasetRows(providerId: UUID): List<ResultRow> =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.providerId eq providerId }
            .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun listClaimableDatasetRows(): List<ResultRow> =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.status inList listOf("in_progress", "reviewing") }
            .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun findProviderDataset(providerId: UUID, datasetId: UUID): ProviderDatasetRecord? =
        DatasetsTable
            .selectAll()
            .where {
                (DatasetsTable.id eq datasetId) and (DatasetsTable.providerId eq providerId)
            }
            .limit(1)
            .firstOrNull()
            ?.let { row ->
                ProviderDatasetRecord(
                    id = row[DatasetsTable.id],
                    status = row[DatasetsTable.status],
                )
            }

    fun findDatasetRow(datasetId: UUID): ResultRow? =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.id eq datasetId }
            .limit(1)
            .firstOrNull()

    fun updateDatasetItemCount(datasetId: UUID, itemCount: Int, now: OffsetDateTime) {
        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[DatasetsTable.itemCount] = itemCount
            it[updatedAt] = now
        }
    }

    fun updateDatasetStatus(datasetId: UUID, status: String, now: OffsetDateTime) {
        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[DatasetsTable.status] = status
            it[updatedAt] = now
        }
    }
}

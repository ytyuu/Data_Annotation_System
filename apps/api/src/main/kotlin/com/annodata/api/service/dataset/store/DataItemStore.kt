package com.annodata.api.service.dataset.store

import com.annodata.api.db.AnnotationTasksTable
import com.annodata.api.db.AnnotationsTable
import com.annodata.api.db.DataItemsTable
import org.jetbrains.exposed.sql.Exists
import org.jetbrains.exposed.sql.NotExists
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

internal class DataItemStore {
    fun listDatasetItemRows(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where { DataItemsTable.datasetId eq datasetId }
            .orderBy(DataItemsTable.createdAt to SortOrder.DESC)
            .toList()

    fun insertDataItems(datasetId: UUID, items: List<com.annodata.api.models.DataItemInput>, now: OffsetDateTime) {
        DataItemsTable.batchInsert(items) { item ->
            this[DataItemsTable.id] = UUID.randomUUID()
            this[DataItemsTable.datasetId] = datasetId
            this[DataItemsTable.content] = item.content
            this[DataItemsTable.contentType] = item.contentType
            this[DataItemsTable.metadata] = item.metadata
            this[DataItemsTable.currentRoundNo] = 1
            this[DataItemsTable.status] = "pending"
            this[DataItemsTable.createdAt] = now
            this[DataItemsTable.updatedAt] = now
        }
    }

    fun countDatasetItems(datasetId: UUID): Int =
        DataItemsTable
            .selectAll()
            .where { DataItemsTable.datasetId eq datasetId }
            .count()
            .toInt()

    fun findDataItemRow(datasetId: UUID, itemId: UUID): ResultRow? =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.id eq itemId) and
                    (DataItemsTable.datasetId eq datasetId)
            }
            .limit(1)
            .firstOrNull()

    fun updateDataItemReviewStatus(itemId: UUID, accepted: Boolean, now: OffsetDateTime) {
        DataItemsTable.update({ DataItemsTable.id eq itemId }) {
            it[DataItemsTable.status] = if (accepted) "accepted" else "rejected"
            it[updatedAt] = now
        }
    }

    fun countDatasetItemsByStatuses(datasetId: UUID, statuses: List<String>): Int =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status inList statuses)
            }
            .count()
            .toInt()

    fun hasNonAcceptedItems(datasetId: UUID): Boolean =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status neq "accepted")
            }
            .limit(1)
            .any()

    fun listRejectedItemRows(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status eq "rejected")
            }
            .toList()

    fun republishRejectedItems(rejectedItems: List<ResultRow>, now: OffsetDateTime) {
        rejectedItems.forEach { item ->
            DataItemsTable.update({ DataItemsTable.id eq item[DataItemsTable.id] }) {
                it[currentRoundNo] = item[DataItemsTable.currentRoundNo] + 1
                it[status] = "pending"
                it[finalResult] = null
                it[finalizedAt] = null
                it[finalizedBy] = null
                it[updatedAt] = now
            }
        }
    }

    fun listDisputedItemRows(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status eq "disputed")
            }
            .orderBy(DataItemsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun listReviewableItemRowsForProvider(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status inList listOf("annotated", "accepted", "rejected"))
            }
            .orderBy(DataItemsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun countClaimablePendingItemsByDataset(annotatorId: UUID, datasetIds: List<UUID>): Map<UUID, Int> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        val itemCount = DataItemsTable.id.count()

        return DataItemsTable
            .select(DataItemsTable.datasetId, itemCount)
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.status eq "pending") and
                    NotExists(
                        AnnotationTasksTable
                            .select(AnnotationTasksTable.id)
                            .where {
                                (AnnotationTasksTable.annotatorId eq annotatorId) and
                                    (AnnotationTasksTable.itemId eq DataItemsTable.id) and
                                    (AnnotationTasksTable.roundNo eq DataItemsTable.currentRoundNo)
                            }
                    )
            }
            .groupBy(DataItemsTable.datasetId)
            .associate { row ->
                row[DataItemsTable.datasetId] to row[itemCount].toInt()
            }
    }

    fun claimPendingItemsForAnnotation(
        datasetId: UUID,
        annotatorId: UUID,
        limit: Int,
        now: OffsetDateTime,
    ): List<ClaimedDataItem> {
        val sql = """
            WITH candidates AS (
                SELECT di.id
                FROM data_items di
                WHERE di.dataset_id = ?
                  AND di.status = 'pending'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM annotation_tasks task
                      WHERE task.annotator_id = ?
                        AND task.item_id = di.id
                        AND task.round_no = di.current_round_no
                  )
                ORDER BY di.created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE data_items di
            SET status = 'assigned',
                updated_at = ?
            FROM candidates c
            WHERE di.id = c.id
            RETURNING
                di.id,
                di.dataset_id,
                di.content,
                di.content_type,
                di.metadata,
                di.current_round_no,
                di.final_result,
                di.finalized_at,
                di.finalized_by,
                di.status,
                di.created_at,
                di.updated_at
        """.trimIndent()

        return TransactionManager.current().exec(
            stmt = sql,
            args = listOf(
                DataItemsTable.datasetId.columnType to datasetId,
                AnnotationTasksTable.annotatorId.columnType to annotatorId,
                DataItemsTable.currentRoundNo.columnType to limit,
                DataItemsTable.updatedAt.columnType to now,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        ClaimedDataItem(
                            id = rs.getObject("id", UUID::class.java),
                            datasetId = rs.getObject("dataset_id", UUID::class.java),
                            content = rs.getString("content"),
                            contentType = rs.getString("content_type"),
                            metadata = rs.getString("metadata"),
                            currentRoundNo = rs.getInt("current_round_no"),
                            finalResult = rs.getString("final_result"),
                            finalizedAt = rs.getObject("finalized_at", OffsetDateTime::class.java),
                            finalizedBy = rs.getObject("finalized_by", UUID::class.java),
                            status = rs.getString("status"),
                            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    fun findReviewableItemRows(
        datasetIds: List<UUID>,
        annotatorId: UUID,
        limit: Int? = null,
    ): List<ResultRow> {
        if (datasetIds.isEmpty()) {
            return emptyList()
        }

        val query = DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.status inList listOf("assigned", "annotated", "disputed")) and
                    Exists(
                        AnnotationsTable
                            .select(AnnotationsTable.id)
                            .where {
                                (AnnotationsTable.itemId eq DataItemsTable.id) and
                                    (AnnotationsTable.roundNo eq DataItemsTable.currentRoundNo) and
                                    (AnnotationsTable.annotationType eq "annotation") and
                                    (AnnotationsTable.status eq "submitted")
                            }
                    ) and
                    NotExists(
                        AnnotationsTable
                            .select(AnnotationsTable.id)
                            .where {
                                (AnnotationsTable.itemId eq DataItemsTable.id) and
                                    (AnnotationsTable.roundNo eq DataItemsTable.currentRoundNo) and
                                    (AnnotationsTable.annotationType eq "review")
                            }
                    ) and
                    NotExists(
                        AnnotationTasksTable
                            .select(AnnotationTasksTable.id)
                            .where {
                                (AnnotationTasksTable.annotatorId eq annotatorId) and
                                    (AnnotationTasksTable.itemId eq DataItemsTable.id) and
                                    (AnnotationTasksTable.roundNo eq DataItemsTable.currentRoundNo)
                            }
                    )
            }
            .orderBy(DataItemsTable.createdAt to SortOrder.ASC)

        return if (limit == null) query.toList() else query.limit(limit).toList()
    }

    fun countReviewableItemsByDataset(annotatorId: UUID, datasetIds: List<UUID>): Map<UUID, Int> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        val itemCount = DataItemsTable.id.count()

        return DataItemsTable
            .select(DataItemsTable.datasetId, itemCount)
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.status inList listOf("assigned", "annotated", "disputed")) and
                    Exists(
                        AnnotationsTable
                            .select(AnnotationsTable.id)
                            .where {
                                (AnnotationsTable.itemId eq DataItemsTable.id) and
                                    (AnnotationsTable.roundNo eq DataItemsTable.currentRoundNo) and
                                    (AnnotationsTable.annotationType eq "annotation") and
                                    (AnnotationsTable.status eq "submitted")
                            }
                    ) and
                    NotExists(
                        AnnotationsTable
                            .select(AnnotationsTable.id)
                            .where {
                                (AnnotationsTable.itemId eq DataItemsTable.id) and
                                    (AnnotationsTable.roundNo eq DataItemsTable.currentRoundNo) and
                                    (AnnotationsTable.annotationType eq "review")
                            }
                    ) and
                    NotExists(
                        AnnotationTasksTable
                            .select(AnnotationTasksTable.id)
                            .where {
                                (AnnotationTasksTable.annotatorId eq annotatorId) and
                                    (AnnotationTasksTable.itemId eq DataItemsTable.id) and
                                    (AnnotationTasksTable.roundNo eq DataItemsTable.currentRoundNo)
                            }
                    )
            }
            .groupBy(DataItemsTable.datasetId)
            .associate { row ->
                row[DataItemsTable.datasetId] to row[itemCount].toInt()
            }
    }

    fun listItemsByIds(itemIds: Set<UUID>): Map<UUID, ResultRow> {
        if (itemIds.isEmpty()) {
            return emptyMap()
        }

        return DataItemsTable
            .selectAll()
            .where { DataItemsTable.id inList itemIds }
            .associateBy { it[DataItemsTable.id] }
    }

    fun markItemDisputed(itemId: UUID, now: OffsetDateTime) {
        DataItemsTable.update({ DataItemsTable.id eq itemId }) {
            it[status] = "disputed"
            it[finalResult] = null
            it[finalizedAt] = null
            it[finalizedBy] = null
            it[updatedAt] = now
        }
    }

    fun markItemAnnotated(itemId: UUID, finalResult: String, now: OffsetDateTime) {
        DataItemsTable.update({ DataItemsTable.id eq itemId }) {
            it[DataItemsTable.status] = "annotated"
            it[DataItemsTable.finalResult] = finalResult
            it[DataItemsTable.finalizedAt] = now
            it[DataItemsTable.finalizedBy] = null
            it[DataItemsTable.updatedAt] = now
        }
    }
}

package com.annodata.api.service.dataset.store

import com.annodata.api.db.AnnotationTaskBatchesTable
import com.annodata.api.db.AnnotationTasksTable
import com.annodata.api.db.AnnotationsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.DatasetsTable
import com.annodata.api.db.DatasetReviewsTable
import com.annodata.api.db.UsersTable
import org.jetbrains.exposed.sql.Exists
import org.jetbrains.exposed.sql.NotExists
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

internal data class TaskStatusCounts(
    val assigned: Int = 0,
    val inProgress: Int = 0,
    val submitted: Int = 0,
)

internal data class ProviderDatasetRecord(
    val id: UUID,
    val status: String,
)

internal class ProviderDatasetStore {
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

    fun listDatasetItemRows(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where { DataItemsTable.datasetId eq datasetId }
            .orderBy(DataItemsTable.createdAt to SortOrder.DESC)
            .toList()

    fun insertDataItems(datasetId: UUID, items: List<com.annodata.api.models.DataItemInput>, now: OffsetDateTime) {
        items.forEach { item ->
            DataItemsTable.insert {
                it[id] = UUID.randomUUID()
                it[DataItemsTable.datasetId] = datasetId
                it[content] = item.content
                it[contentType] = item.contentType
                it[metadata] = item.metadata
                it[currentRoundNo] = 1
                it[status] = "pending"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    fun countDatasetItems(datasetId: UUID): Int =
        DataItemsTable
            .selectAll()
            .where { DataItemsTable.datasetId eq datasetId }
            .count()
            .toInt()

    fun updateDatasetItemCount(datasetId: UUID, itemCount: Int, now: OffsetDateTime) {
        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[DatasetsTable.itemCount] = itemCount
            it[updatedAt] = now
        }
    }

    fun findDatasetRow(datasetId: UUID): ResultRow? =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.id eq datasetId }
            .limit(1)
            .firstOrNull()

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

    fun updateDatasetStatus(datasetId: UUID, status: String, now: OffsetDateTime) {
        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[DatasetsTable.status] = status
            it[updatedAt] = now
        }
    }

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

    fun listDisputedItemRows(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status eq "disputed")
            }
            .orderBy(DataItemsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun listCurrentRoundAnnotationsForItem(itemId: UUID, roundNo: Int): List<ResultRow> =
        AnnotationsTable
            .selectAll()
            .where {
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.roundNo eq roundNo) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }
            .orderBy(AnnotationsTable.annotationType to SortOrder.ASC)
            .toList()

    fun loadUserDisplayNames(userIds: Set<UUID>): Map<String, String> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        return UsersTable
            .select(UsersTable.id, UsersTable.displayName)
            .where { UsersTable.id inList userIds }
            .associate { it[UsersTable.id].toString() to it[UsersTable.displayName] }
    }

    fun listReviewableItemRowsForProvider(datasetId: UUID): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status inList listOf("annotated", "accepted", "rejected"))
            }
            .orderBy(DataItemsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun listCurrentRoundAnnotationsByItem(items: List<ResultRow>): Map<UUID, List<ResultRow>> {
        val itemIds = items.map { it[DataItemsTable.id] }
        if (itemIds.isEmpty()) {
            return emptyMap()
        }

        val annRows = AnnotationsTable
            .select(
                AnnotationsTable.id,
                AnnotationsTable.itemId,
                AnnotationsTable.annotatorId,
                AnnotationsTable.roundNo,
                AnnotationsTable.annotationType,
                AnnotationsTable.result,
                AnnotationsTable.comment,
                AnnotationsTable.isDisputed,
                AnnotationsTable.status,
                AnnotationsTable.submittedAt,
            )
            .where {
                (AnnotationsTable.itemId inList itemIds) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }
            .orderBy(AnnotationsTable.annotationType to SortOrder.ASC)
            .toList()
            .filter { ann ->
                items.any { item ->
                    item[DataItemsTable.id] == ann[AnnotationsTable.itemId] &&
                        item[DataItemsTable.currentRoundNo] == ann[AnnotationsTable.roundNo]
                }
            }

        return annRows.groupBy { it[AnnotationsTable.itemId] }
    }
}

internal class AnnotatorDatasetStore {
    fun listClaimableDatasetRows(): List<ResultRow> =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.status inList listOf("in_progress", "reviewing") }
            .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
            .toList()

    fun findDatasetRow(datasetId: UUID): ResultRow? =
        DatasetsTable
            .selectAll()
            .where { DatasetsTable.id eq datasetId }
            .limit(1)
            .firstOrNull()

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

    fun listClaimablePendingItemRows(datasetId: UUID, annotatorId: UUID, limit: Int): List<ResultRow> =
        DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
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
            .orderBy(DataItemsTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .toList()

    fun countActiveBatchItems(annotatorId: UUID, activeBatchStatuses: List<String>): Int {
        val totalCountSum = AnnotationTaskBatchesTable.totalCount.sum()

        return AnnotationTaskBatchesTable
            .select(totalCountSum)
            .where {
                (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                    (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
            }
            .firstOrNull()
            ?.get(totalCountSum)
            ?: 0
    }

    fun countActiveBatchesByDataset(
        annotatorId: UUID,
        datasetIds: List<UUID>,
        activeBatchStatuses: List<String>,
        batchType: String? = null,
    ): Map<UUID, Long> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        val batchCount = AnnotationTaskBatchesTable.id.count()

        return AnnotationTaskBatchesTable
            .select(AnnotationTaskBatchesTable.datasetId, batchCount)
            .where {
                val baseCondition =
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                        (AnnotationTaskBatchesTable.datasetId inList datasetIds) and
                        (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
                if (batchType == null) {
                    baseCondition
                } else {
                    baseCondition and (AnnotationTaskBatchesTable.batchType eq batchType)
                }
            }
            .groupBy(AnnotationTaskBatchesTable.datasetId)
            .associate { row ->
                row[AnnotationTaskBatchesTable.datasetId] to row[batchCount]
            }
    }

    fun countExistingActiveBatchesInDataset(
        annotatorId: UUID,
        datasetId: UUID,
        taskType: String,
        activeBatchStatuses: List<String>,
    ): Long =
        AnnotationTaskBatchesTable
            .selectAll()
            .where {
                (AnnotationTaskBatchesTable.annotatorId eq annotatorId) and
                    (AnnotationTaskBatchesTable.datasetId eq datasetId) and
                    (AnnotationTaskBatchesTable.batchType eq taskType) and
                    (AnnotationTaskBatchesTable.status inList activeBatchStatuses)
            }
            .count()

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

    fun claimPendingItems(items: List<ResultRow>, now: OffsetDateTime): List<ResultRow> =
        items.filter { item ->
            DataItemsTable.update({
                (DataItemsTable.id eq item[DataItemsTable.id]) and
                    (DataItemsTable.status eq "pending")
            }) {
                it[status] = "assigned"
                it[updatedAt] = now
            } > 0
        }

    fun createTaskBatch(
        batchId: UUID,
        orderNo: String,
        datasetId: UUID,
        annotatorId: UUID,
        taskType: String,
        totalCount: Int,
        now: OffsetDateTime,
    ) {
        AnnotationTaskBatchesTable.insert {
            it[id] = batchId
            it[AnnotationTaskBatchesTable.orderNo] = orderNo
            it[AnnotationTaskBatchesTable.datasetId] = datasetId
            it[AnnotationTaskBatchesTable.annotatorId] = annotatorId
            it[batchType] = taskType
            it[status] = "assigned"
            it[AnnotationTaskBatchesTable.totalCount] = totalCount
            it[assignedAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    fun createAnnotationTask(
        taskId: UUID,
        batchId: UUID,
        datasetId: UUID,
        itemId: UUID,
        annotatorId: UUID,
        roundNo: Int,
        now: OffsetDateTime,
    ) {
        AnnotationTasksTable.insert {
            it[id] = taskId
            it[AnnotationTasksTable.batchId] = batchId
            it[AnnotationTasksTable.datasetId] = datasetId
            it[AnnotationTasksTable.itemId] = itemId
            it[AnnotationTasksTable.annotatorId] = annotatorId
            it[AnnotationTasksTable.roundNo] = roundNo
            it[status] = "assigned"
            it[assignedAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    fun findBatchRowForAnnotator(batchId: UUID, annotatorId: UUID): ResultRow? =
        AnnotationTaskBatchesTable
            .selectAll()
            .where {
                (AnnotationTaskBatchesTable.id eq batchId) and
                    (AnnotationTaskBatchesTable.annotatorId eq annotatorId)
            }
            .limit(1)
            .firstOrNull()

    fun listTaskRowsForBatch(annotatorId: UUID, batchId: UUID): List<ResultRow> =
        AnnotationTasksTable
            .select(
                AnnotationTasksTable.id,
                AnnotationTasksTable.itemId,
                AnnotationTasksTable.status,
                AnnotationTasksTable.assignedAt,
                AnnotationTasksTable.startedAt,
                AnnotationTasksTable.submittedAt,
            )
            .where {
                (AnnotationTasksTable.annotatorId eq annotatorId) and
                    (AnnotationTasksTable.batchId eq batchId)
            }
            .orderBy(AnnotationTasksTable.assignedAt to SortOrder.ASC)
            .toList()

    fun listTaskRowsForBatchByIds(annotatorId: UUID, batchId: UUID, taskIds: List<UUID>): List<ResultRow> =
        AnnotationTasksTable
            .selectAll()
            .where {
                (AnnotationTasksTable.annotatorId eq annotatorId) and
                    (AnnotationTasksTable.batchId eq batchId) and
                    (AnnotationTasksTable.id inList taskIds)
            }
            .toList()

    fun listAnnotationsByTaskIds(taskIds: List<UUID>): Map<UUID, ResultRow> {
        if (taskIds.isEmpty()) {
            return emptyMap()
        }

        return AnnotationsTable
            .select(
                AnnotationsTable.taskId,
                AnnotationsTable.result,
                AnnotationsTable.isDisputed,
                AnnotationsTable.status,
                AnnotationsTable.adoptionStatus,
                AnnotationsTable.adoptionComment,
            )
            .where { AnnotationsTable.taskId inList taskIds }
            .associateBy { it[AnnotationsTable.taskId] }
    }

    fun countTaskStatusesByBatch(batchIds: Set<UUID>): Map<UUID, TaskStatusCounts> {
        if (batchIds.isEmpty()) {
            return emptyMap()
        }

        val taskCount = AnnotationTasksTable.id.count()
        val counts = mutableMapOf<UUID, TaskStatusCounts>()

        AnnotationTasksTable
            .select(AnnotationTasksTable.batchId, AnnotationTasksTable.status, taskCount)
            .where { AnnotationTasksTable.batchId inList batchIds }
            .groupBy(AnnotationTasksTable.batchId, AnnotationTasksTable.status)
            .forEach { row ->
                val batchId = row[AnnotationTasksTable.batchId]
                val count = row[taskCount].toInt()
                val current = counts[batchId] ?: TaskStatusCounts()
                counts[batchId] = when (row[AnnotationTasksTable.status]) {
                    "assigned" -> current.copy(assigned = count)
                    "in_progress" -> current.copy(inProgress = count)
                    "submitted" -> current.copy(submitted = count)
                    else -> current
                }
            }

        return counts
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

    fun findExistingAnnotationByTaskId(taskId: UUID): ResultRow? =
        AnnotationsTable
            .selectAll()
            .where { AnnotationsTable.taskId eq taskId }
            .limit(1)
            .firstOrNull()

    fun findOriginalAnnotation(itemId: UUID, roundNo: Int): ResultRow? =
        AnnotationsTable
            .selectAll()
            .where {
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.annotationType eq "annotation") and
                    (AnnotationsTable.roundNo eq roundNo)
            }
            .orderBy(AnnotationsTable.submittedAt to SortOrder.ASC)
            .limit(1)
            .firstOrNull()

    fun upsertSubmittedAnnotation(
        existingAnnotation: ResultRow?,
        taskId: UUID,
        itemId: UUID,
        annotatorId: UUID,
        result: String,
        annotationType: String,
        reviewOfAnnotationId: UUID?,
        roundNo: Int,
        comment: String?,
        isDisputed: Boolean,
        reviewedAt: OffsetDateTime?,
        now: OffsetDateTime,
    ) {
        if (existingAnnotation == null) {
            AnnotationsTable.insert {
                it[id] = UUID.randomUUID()
                it[AnnotationsTable.taskId] = taskId
                it[AnnotationsTable.itemId] = itemId
                it[AnnotationsTable.annotatorId] = annotatorId
                it[AnnotationsTable.result] = result
                it[AnnotationsTable.annotationType] = annotationType
                if (reviewOfAnnotationId != null) {
                    it[AnnotationsTable.reviewOfAnnotationId] = reviewOfAnnotationId
                }
                it[AnnotationsTable.roundNo] = roundNo
                it[AnnotationsTable.comment] = comment
                it[AnnotationsTable.isDisputed] = isDisputed
                it[AnnotationsTable.status] = "submitted"
                it[submittedAt] = now
                if (reviewedAt != null) {
                    it[AnnotationsTable.reviewedAt] = reviewedAt
                }
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            AnnotationsTable.update({ AnnotationsTable.taskId eq taskId }) {
                it[AnnotationsTable.result] = result
                it[AnnotationsTable.annotationType] = annotationType
                it[AnnotationsTable.reviewOfAnnotationId] = reviewOfAnnotationId
                it[AnnotationsTable.roundNo] = roundNo
                it[AnnotationsTable.comment] = comment
                it[AnnotationsTable.isDisputed] = isDisputed
                it[AnnotationsTable.status] = "submitted"
                it[submittedAt] = now
                if (reviewedAt != null) {
                    it[AnnotationsTable.reviewedAt] = reviewedAt
                }
                it[updatedAt] = now
            }
        }
    }

    fun markTaskSubmitted(taskId: UUID, now: OffsetDateTime) {
        AnnotationTasksTable.update({ AnnotationTasksTable.id eq taskId }) {
            it[status] = "submitted"
            it[submittedAt] = now
            it[updatedAt] = now
        }
    }

    fun countSubmittedTasksInBatch(batchId: UUID): Int =
        AnnotationTasksTable
            .selectAll()
            .where {
                (AnnotationTasksTable.batchId eq batchId) and
                    (AnnotationTasksTable.status eq "submitted")
            }
            .count()
            .toInt()

    fun markBatchInProgress(batchId: UUID, startedAtMissing: Boolean, now: OffsetDateTime) {
        AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
            it[status] = "in_progress"
            if (startedAtMissing) {
                it[startedAt] = now
            }
            it[updatedAt] = now
        }
    }

    fun markBatchSubmitted(batchId: UUID, now: OffsetDateTime) {
        AnnotationTaskBatchesTable.update({ AnnotationTaskBatchesTable.id eq batchId }) {
            it[status] = "submitted"
            it[submittedAt] = now
            it[updatedAt] = now
        }
    }

    fun listRoundAnnotations(itemId: UUID, roundNo: Int): List<ResultRow> =
        AnnotationsTable
            .selectAll()
            .where {
                (AnnotationsTable.itemId eq itemId) and
                    (AnnotationsTable.roundNo eq roundNo) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }
            .toList()

    fun markRoundAnnotationsDisputed(itemId: UUID, roundNo: Int, now: OffsetDateTime) {
        AnnotationsTable.update({
            (AnnotationsTable.itemId eq itemId) and
                (AnnotationsTable.roundNo eq roundNo) and
                (AnnotationsTable.annotationType inList listOf("annotation", "review"))
        }) {
            it[AnnotationsTable.isDisputed] = true
            it[status] = "submitted"
            it[adoptionStatus] = 0.toShort()
            it[adoptedAt] = null
            it[adoptedBy] = null
            it[adoptionComment] = null
            it[reviewedAt] = now
            it[updatedAt] = now
        }
    }

    fun markRoundAnnotationsAccepted(itemId: UUID, roundNo: Int, now: OffsetDateTime) {
        AnnotationsTable.update({
            (AnnotationsTable.itemId eq itemId) and
                (AnnotationsTable.roundNo eq roundNo) and
                (AnnotationsTable.annotationType inList listOf("annotation", "review"))
        }) {
            it[AnnotationsTable.isDisputed] = false
            it[status] = "accepted"
            it[adoptionStatus] = 1.toShort()
            it[adoptedAt] = now
            it[adoptedBy] = null
            it[adoptionComment] = "原始标注与互查一致，系统自动采纳"
            it[reviewedAt] = now
            it[updatedAt] = now
        }
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
            // 这里显式使用 DataItemsTable.finalResult，避免与同名方法参数冲突，
            // 否则 Exposed 会把参数字符串当成列对象位置的实参，触发类型推断错误。
            it[DataItemsTable.status] = "annotated"
            it[DataItemsTable.finalResult] = finalResult
            it[DataItemsTable.finalizedAt] = now
            it[DataItemsTable.finalizedBy] = null
            it[DataItemsTable.updatedAt] = now
        }
    }
}

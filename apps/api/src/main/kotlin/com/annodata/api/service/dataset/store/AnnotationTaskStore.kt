package com.annodata.api.service.dataset.store

import com.annodata.api.db.AnnotationTaskBatchesTable
import com.annodata.api.db.AnnotationTasksTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

internal class AnnotationTaskStore {
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
}

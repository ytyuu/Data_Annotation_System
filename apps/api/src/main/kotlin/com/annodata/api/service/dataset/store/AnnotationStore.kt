package com.annodata.api.service.dataset.store

import com.annodata.api.db.AnnotationsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

internal class AnnotationStore {
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

    fun listCurrentRoundAnnotationsByItem(items: List<ResultRow>): Map<UUID, List<ResultRow>> {
        val itemIds = items.map { it[DataItemsTable.id] }
        if (itemIds.isEmpty()) {
            return emptyMap()
        }

        val annRows = AnnotationsTable
            .join(
                otherTable = DataItemsTable,
                joinType = JoinType.INNER,
                additionalConstraint = { AnnotationsTable.itemId eq DataItemsTable.id },
            )
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
                (DataItemsTable.id inList itemIds) and
                    (AnnotationsTable.roundNo eq DataItemsTable.currentRoundNo) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review"))
            }
            .orderBy(AnnotationsTable.annotationType to SortOrder.ASC)
            .toList()

        return annRows.groupBy { it[AnnotationsTable.itemId] }
    }

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

    fun loadUserDisplayNames(userIds: Set<UUID>): Map<String, String> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        return UsersTable
            .select(UsersTable.id, UsersTable.displayName)
            .where { UsersTable.id inList userIds }
            .associate { it[UsersTable.id].toString() to it[UsersTable.displayName] }
    }
}

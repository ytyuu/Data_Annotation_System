package com.annodata.api.service.ai.store

import com.annodata.api.db.AiAnnotationBatchesTable
import com.annodata.api.db.AiAnnotationResultsTable
import com.annodata.api.db.DataItemsTable
import com.annodata.api.db.DatasetsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

internal data class LockedAiDataItem(val id: UUID, val roundNo: Int)

internal data class ClaimedAiWorkItem(
    val resultId: UUID,
    val itemId: UUID,
    val roundNo: Int,
    val content: String,
    val contentType: String,
    val metadata: String,
)

internal class AiAnnotationStore {
    fun findProviderDataset(providerId: UUID, datasetId: UUID): ResultRow? =
        DatasetsTable
            .selectAll()
            .where { (DatasetsTable.id eq datasetId) and (DatasetsTable.providerId eq providerId) }
            .limit(1)
            .firstOrNull()

    fun lockPendingTextItems(datasetId: UUID, limit: Int, now: OffsetDateTime): List<LockedAiDataItem> {
        val sql = """
            WITH candidates AS (
                SELECT di.id
                FROM data_items di
                WHERE di.dataset_id = ?
                  AND di.status = 'pending'
                  AND di.content_type = 'text'
                ORDER BY di.created_at, di.id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE data_items di
            SET status = 'ai_processing',
                updated_at = ?
            FROM candidates c
            WHERE di.id = c.id
            RETURNING di.id, di.current_round_no
        """.trimIndent()

        return TransactionManager.current().exec(
            stmt = sql,
            args = listOf(
                DataItemsTable.datasetId.columnType to datasetId,
                DataItemsTable.currentRoundNo.columnType to limit,
                DataItemsTable.updatedAt.columnType to now,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(LockedAiDataItem(rs.getObject("id", UUID::class.java), rs.getInt("current_round_no")))
                }
            }
        } ?: emptyList()
    }

    fun insertBatch(
        batchId: UUID,
        datasetId: UUID,
        providerId: UUID,
        modelName: String,
        promptVersion: String,
        schemaSnapshot: String,
        guideSnapshot: String?,
        config: String,
        totalCount: Int,
        now: OffsetDateTime,
    ) {
        AiAnnotationBatchesTable.insert {
            it[id] = batchId
            it[AiAnnotationBatchesTable.datasetId] = datasetId
            it[AiAnnotationBatchesTable.providerId] = providerId
            it[status] = "pending"
            it[modelProvider] = "deepseek"
            it[AiAnnotationBatchesTable.modelName] = modelName
            it[AiAnnotationBatchesTable.promptVersion] = promptVersion
            it[annotationSchemaSnapshot] = schemaSnapshot
            it[annotationGuideSnapshot] = guideSnapshot
            it[AiAnnotationBatchesTable.config] = config
            it[AiAnnotationBatchesTable.totalCount] = totalCount
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    fun insertPendingResults(
        batchId: UUID,
        datasetId: UUID,
        items: List<LockedAiDataItem>,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.batchInsert(items) { item ->
            this[AiAnnotationResultsTable.id] = UUID.randomUUID()
            this[AiAnnotationResultsTable.batchId] = batchId
            this[AiAnnotationResultsTable.datasetId] = datasetId
            this[AiAnnotationResultsTable.itemId] = item.id
            this[AiAnnotationResultsTable.roundNo] = item.roundNo
            this[AiAnnotationResultsTable.status] = "pending"
            this[AiAnnotationResultsTable.createdAt] = now
            this[AiAnnotationResultsTable.updatedAt] = now
        }
    }

    fun listProviderBatches(providerId: UUID, datasetId: UUID): List<ResultRow> =
        (AiAnnotationBatchesTable innerJoin DatasetsTable)
            .selectAll()
            .where {
                (AiAnnotationBatchesTable.providerId eq providerId) and
                    (AiAnnotationBatchesTable.datasetId eq datasetId)
            }
            .orderBy(AiAnnotationBatchesTable.createdAt to SortOrder.DESC)
            .toList()

    fun findProviderBatch(providerId: UUID, batchId: UUID): ResultRow? =
        (AiAnnotationBatchesTable innerJoin DatasetsTable)
            .selectAll()
            .where {
                (AiAnnotationBatchesTable.providerId eq providerId) and
                    (AiAnnotationBatchesTable.id eq batchId)
            }
            .limit(1)
            .firstOrNull()

    fun findBatch(batchId: UUID): ResultRow? =
        AiAnnotationBatchesTable
            .selectAll()
            .where { AiAnnotationBatchesTable.id eq batchId }
            .limit(1)
            .firstOrNull()

    fun markBatchRunning(batchId: UUID, setStartedAt: Boolean, now: OffsetDateTime) {
        AiAnnotationBatchesTable.update({ AiAnnotationBatchesTable.id eq batchId }) {
            it[status] = "running"
            if (setStartedAt) it[startedAt] = now
            it[finishedAt] = null
            it[updatedAt] = now
        }
    }

    fun markExhaustedLeasesFailed(batchId: UUID, maxAttempts: Int, now: OffsetDateTime) {
        AiAnnotationResultsTable.update({
            (AiAnnotationResultsTable.batchId eq batchId) and
                (AiAnnotationResultsTable.status eq "processing") and
                (AiAnnotationResultsTable.leaseExpiresAt lessEq now) and
                (AiAnnotationResultsTable.attemptCount greaterEq maxAttempts)
        }) {
            it[status] = "failed"
            it[errorMessage] = "Worker 领取次数已达到上限"
            it[leasedAt] = null
            it[leaseExpiresAt] = null
            it[updatedAt] = now
        }
    }

    fun claimWorkItems(
        batchId: UUID,
        maxAttempts: Int,
        limit: Int,
        now: OffsetDateTime,
        leaseExpiresAt: OffsetDateTime,
    ): List<ClaimedAiWorkItem> {
        val sql = """
            WITH candidates AS (
                SELECT result.id
                FROM ai_annotation_results result
                WHERE result.batch_id = ?
                  AND result.attempt_count < ?
                  AND (
                    result.status = 'pending'
                    OR (result.status = 'processing' AND result.lease_expires_at <= ?)
                  )
                ORDER BY result.created_at, result.id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE ai_annotation_results result
                SET status = 'processing',
                    attempt_count = result.attempt_count + 1,
                    leased_at = ?,
                    lease_expires_at = ?,
                    error_message = NULL,
                    updated_at = ?
                FROM candidates c
                WHERE result.id = c.id
                RETURNING result.id, result.item_id, result.round_no
            )
            SELECT
                claimed.id AS result_id,
                claimed.item_id,
                claimed.round_no,
                item.content,
                item.content_type,
                item.metadata
            FROM claimed
            JOIN data_items item ON item.id = claimed.item_id
            ORDER BY claimed.id
        """.trimIndent()

        return TransactionManager.current().exec(
            stmt = sql,
            args = listOf(
                AiAnnotationResultsTable.batchId.columnType to batchId,
                AiAnnotationResultsTable.attemptCount.columnType to maxAttempts,
                AiAnnotationResultsTable.leaseExpiresAt.columnType to now,
                AiAnnotationResultsTable.attemptCount.columnType to limit,
                AiAnnotationResultsTable.leasedAt.columnType to now,
                AiAnnotationResultsTable.leaseExpiresAt.columnType to leaseExpiresAt,
                AiAnnotationResultsTable.updatedAt.columnType to now,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        ClaimedAiWorkItem(
                            resultId = rs.getObject("result_id", UUID::class.java),
                            itemId = rs.getObject("item_id", UUID::class.java),
                            roundNo = rs.getInt("round_no"),
                            content = rs.getString("content"),
                            contentType = rs.getString("content_type"),
                            metadata = rs.getString("metadata"),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    fun findResultWithItem(batchId: UUID, resultId: UUID): ResultRow? =
        (AiAnnotationResultsTable innerJoin DataItemsTable)
            .selectAll()
            .where {
                (AiAnnotationResultsTable.batchId eq batchId) and
                    (AiAnnotationResultsTable.id eq resultId)
            }
            .limit(1)
            .firstOrNull()

    fun hasUploadRequest(batchId: UUID, requestId: String): Boolean =
        AiAnnotationResultsTable
            .selectAll()
            .where {
                (AiAnnotationResultsTable.batchId eq batchId) and
                    (AiAnnotationResultsTable.requestId eq requestId)
            }
            .limit(1)
            .any()

    fun markResultSucceeded(
        resultId: UUID,
        status: String,
        result: String,
        resultHash: String,
        confidence: String,
        confidenceScore: java.math.BigDecimal,
        reason: String,
        needsHumanReview: Boolean,
        isSampled: Boolean,
        riskFlags: String,
        rawOutput: String?,
        requestId: String,
        chunkNo: Int,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.update({ AiAnnotationResultsTable.id eq resultId }) {
            it[AiAnnotationResultsTable.status] = status
            it[AiAnnotationResultsTable.result] = result
            it[AiAnnotationResultsTable.resultHash] = resultHash
            it[AiAnnotationResultsTable.confidence] = confidence
            it[AiAnnotationResultsTable.confidenceScore] = confidenceScore
            it[AiAnnotationResultsTable.reason] = reason
            it[AiAnnotationResultsTable.needsHumanReview] = needsHumanReview
            it[AiAnnotationResultsTable.isSampled] = isSampled
            it[AiAnnotationResultsTable.riskFlags] = riskFlags
            it[AiAnnotationResultsTable.rawOutput] = rawOutput
            it[AiAnnotationResultsTable.errorMessage] = null
            it[AiAnnotationResultsTable.requestId] = requestId
            it[AiAnnotationResultsTable.chunkNo] = chunkNo
            it[leasedAt] = null
            it[leaseExpiresAt] = null
            it[reviewedBy] = null
            it[reviewedAt] = null
            it[reviewAction] = null
            it[reviewComment] = null
            it[updatedAt] = now
        }
    }

    fun markResultFailed(
        resultId: UUID,
        resultHash: String,
        errorMessage: String,
        rawOutput: String?,
        requestId: String,
        chunkNo: Int,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.update({ AiAnnotationResultsTable.id eq resultId }) {
            it[status] = "failed"
            it[AiAnnotationResultsTable.resultHash] = resultHash
            it[AiAnnotationResultsTable.errorMessage] = errorMessage
            it[AiAnnotationResultsTable.rawOutput] = rawOutput
            it[AiAnnotationResultsTable.requestId] = requestId
            it[AiAnnotationResultsTable.chunkNo] = chunkNo
            it[leasedAt] = null
            it[leaseExpiresAt] = null
            it[reviewedBy] = null
            it[reviewedAt] = null
            it[reviewAction] = null
            it[reviewComment] = null
            it[updatedAt] = now
        }
    }

    fun addBatchUsage(
        batchId: UUID,
        modelRequestCount: Int,
        promptTokens: Long,
        completionTokens: Long,
        now: OffsetDateTime,
    ) {
        val sql = """
            UPDATE ai_annotation_batches
            SET model_request_count = model_request_count + ?,
                prompt_tokens = prompt_tokens + ?,
                completion_tokens = completion_tokens + ?,
                updated_at = ?
            WHERE id = ?
        """.trimIndent()
        TransactionManager.current().exec(
            stmt = sql,
            args = listOf(
                AiAnnotationBatchesTable.modelRequestCount.columnType to modelRequestCount,
                AiAnnotationBatchesTable.promptTokens.columnType to promptTokens,
                AiAnnotationBatchesTable.completionTokens.columnType to completionTokens,
                AiAnnotationBatchesTable.updatedAt.columnType to now,
                AiAnnotationBatchesTable.id.columnType to batchId,
            ),
        )
    }

    fun markBatchCompleted(batchId: UUID, now: OffsetDateTime) {
        AiAnnotationBatchesTable.update({ AiAnnotationBatchesTable.id eq batchId }) {
            it[status] = "completed"
            it[finishedAt] = now
            it[updatedAt] = now
        }
    }

    fun failBatchAndRelease(batchId: UUID, errorMessage: String, now: OffsetDateTime) {
        val releaseItemsSql = """
            UPDATE data_items item
            SET status = 'pending',
                updated_at = ?
            WHERE item.status = 'ai_processing'
              AND EXISTS (
                SELECT 1
                FROM ai_annotation_results result
                WHERE result.batch_id = ?
                  AND result.item_id = item.id
                  AND result.status IN ('pending', 'processing')
              )
        """.trimIndent()
        TransactionManager.current().exec(
            stmt = releaseItemsSql,
            args = listOf(
                DataItemsTable.updatedAt.columnType to now,
                AiAnnotationResultsTable.batchId.columnType to batchId,
            ),
        )

        AiAnnotationResultsTable.update({
            (AiAnnotationResultsTable.batchId eq batchId) and
                (AiAnnotationResultsTable.status inList listOf("pending", "processing"))
        }) {
            it[status] = "failed"
            it[AiAnnotationResultsTable.errorMessage] = errorMessage
            it[leasedAt] = null
            it[leaseExpiresAt] = null
            it[updatedAt] = now
        }

        AiAnnotationBatchesTable.update({ AiAnnotationBatchesTable.id eq batchId }) {
            it[status] = "failed"
            it[AiAnnotationBatchesTable.errorMessage] = errorMessage
            it[finishedAt] = now
            it[updatedAt] = now
        }
    }

    fun deleteBatchAndReleaseItems(batchId: UUID, now: OffsetDateTime) {
        val releaseItemsSql = """
            UPDATE data_items item
            SET status = 'pending',
                updated_at = ?
            WHERE item.status = 'ai_processing'
              AND EXISTS (
                SELECT 1
                FROM ai_annotation_results result
                WHERE result.batch_id = ?
                  AND result.item_id = item.id
              )
        """.trimIndent()
        TransactionManager.current().exec(
            stmt = releaseItemsSql,
            args = listOf(
                DataItemsTable.updatedAt.columnType to now,
                AiAnnotationResultsTable.batchId.columnType to batchId,
            ),
        )

        AiAnnotationBatchesTable.deleteWhere { AiAnnotationBatchesTable.id eq batchId }
    }

    fun listProviderResults(
        providerId: UUID,
        batchId: UUID,
        statusFilter: String?,
        reviewMode: String?,
        page: Int,
        pageSize: Int,
    ): Pair<List<ResultRow>, Long> {
        var condition: Op<Boolean> =
            (AiAnnotationBatchesTable.providerId eq providerId) and
                (AiAnnotationResultsTable.batchId eq batchId)
        if (statusFilter != null) condition = condition and (AiAnnotationResultsTable.status eq statusFilter)
        if (reviewMode == "sampling") condition = condition and (AiAnnotationResultsTable.isSampled eq true)
        if (reviewMode == "mandatory") condition = condition and (AiAnnotationResultsTable.needsHumanReview eq true)

        val joined = (AiAnnotationResultsTable innerJoin AiAnnotationBatchesTable) innerJoin DataItemsTable
        val total = joined.selectAll().where { condition }.count()
        val rows = joined
            .selectAll()
            .where { condition }
            .orderBy(AiAnnotationResultsTable.createdAt to SortOrder.ASC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
        return rows to total
    }

    fun findProviderResult(providerId: UUID, resultId: UUID): ResultRow? =
        ((AiAnnotationResultsTable innerJoin AiAnnotationBatchesTable) innerJoin DataItemsTable)
            .selectAll()
            .where {
                (AiAnnotationBatchesTable.providerId eq providerId) and
                    (AiAnnotationResultsTable.id eq resultId)
            }
            .limit(1)
            .firstOrNull()

    fun hasUnreviewedSample(batchId: UUID): Boolean =
        AiAnnotationResultsTable
            .selectAll()
            .where {
                (AiAnnotationResultsTable.batchId eq batchId) and
                    (AiAnnotationResultsTable.isSampled eq true) and
                    (AiAnnotationResultsTable.status inList listOf("ai_labeled", "needs_review"))
            }
            .limit(1)
            .any()

    fun markResultAccepted(
        resultId: UUID,
        providerId: UUID,
        acceptedResult: String?,
        action: String,
        comment: String?,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.update({ AiAnnotationResultsTable.id eq resultId }) {
            it[status] = "accepted"
            it[AiAnnotationResultsTable.acceptedResult] = acceptedResult
            it[reviewedBy] = providerId
            it[reviewedAt] = now
            it[reviewAction] = action
            it[reviewComment] = comment
            it[updatedAt] = now
        }
    }

    fun markDataItemAcceptedFromAi(itemId: UUID, providerId: UUID, finalResult: String, now: OffsetDateTime) {
        DataItemsTable.update({ DataItemsTable.id eq itemId }) {
            it[status] = "annotated"
            it[DataItemsTable.finalResult] = finalResult
            it[finalizedAt] = now
            it[finalizedBy] = providerId
            it[updatedAt] = now
        }
    }

    fun markResultRejectedToHuman(
        resultId: UUID,
        providerId: UUID,
        comment: String?,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.update({ AiAnnotationResultsTable.id eq resultId }) {
            it[status] = "rejected"
            it[reviewedBy] = providerId
            it[reviewedAt] = now
            it[reviewAction] = "reject_to_human"
            it[reviewComment] = comment
            it[updatedAt] = now
        }
    }

    fun releaseDataItemToHuman(itemId: UUID, now: OffsetDateTime) {
        DataItemsTable.update({ DataItemsTable.id eq itemId }) {
            it[status] = "pending"
            it[finalResult] = null
            it[finalizedAt] = null
            it[finalizedBy] = null
            it[updatedAt] = now
        }
    }

    fun resetResultForRetry(
        resultId: UUID,
        providerId: UUID,
        comment: String?,
        now: OffsetDateTime,
    ) {
        AiAnnotationResultsTable.update({ AiAnnotationResultsTable.id eq resultId }) {
            it[status] = "pending"
            it[result] = null
            it[acceptedResult] = null
            it[resultHash] = null
            it[confidence] = null
            it[confidenceScore] = null
            it[reason] = null
            it[needsHumanReview] = false
            it[isSampled] = false
            it[riskFlags] = "[]"
            it[rawOutput] = null
            it[errorMessage] = null
            it[attemptCount] = 0
            it[chunkNo] = null
            it[requestId] = null
            it[leasedAt] = null
            it[leaseExpiresAt] = null
            it[reviewedBy] = providerId
            it[reviewedAt] = now
            it[reviewAction] = "reject_retry"
            it[reviewComment] = comment
            it[updatedAt] = now
        }
    }
}

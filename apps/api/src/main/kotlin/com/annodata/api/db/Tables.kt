package com.annodata.api.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object UsersTable : Table("users") {
    val id = uuid("id")
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 80)
    val role = varchar("role", 24)
    val status = varchar("status", 24)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DatasetsTable : Table("datasets") {
    val id = uuid("id")
    val providerId = uuid("provider_id").references(UsersTable.id)
    val name = varchar("name", 120)
    val description = text("description").nullable()
    val annotationGuide = text("annotation_guide").nullable()
    val annotationSchema = jsonb("annotation_schema")
    val status = varchar("status", 32)
    val targetCompletionRatio = decimal("target_completion_ratio", 5, 2)
    val itemCount = integer("item_count")
    val completedItemCount = integer("completed_item_count")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DataItemsTable : Table("data_items") {
    val id = uuid("id")
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val contentType = varchar("content_type", 32)
    val metadata = jsonb("metadata")
    val currentRoundNo = integer("current_round_no")
    val finalResult = jsonb("final_result").nullable()
    val finalizedAt = timestampWithTimeZone("finalized_at").nullable()
    val finalizedBy = uuid("finalized_by").nullable()
    val status = varchar("status", 32)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AiAnnotationBatchesTable : Table("ai_annotation_batches") {
    val id = uuid("id")
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val providerId = uuid("provider_id").references(UsersTable.id)
    val status = varchar("status", 32)
    val modelProvider = varchar("model_provider", 64)
    val modelName = varchar("model_name", 128)
    val promptVersion = varchar("prompt_version", 64)
    val annotationSchemaSnapshot = jsonb("annotation_schema_snapshot")
    val annotationGuideSnapshot = text("annotation_guide_snapshot").nullable()
    val config = jsonb("config").default("{}")
    val totalCount = integer("total_count").default(0)
    val processedCount = integer("processed_count").default(0)
    val successCount = integer("success_count").default(0)
    val failedCount = integer("failed_count").default(0)
    val needsReviewCount = integer("needs_review_count").default(0)
    val acceptedCount = integer("accepted_count").default(0)
    val rejectedCount = integer("rejected_count").default(0)
    val modelRequestCount = integer("model_request_count").default(0)
    val promptTokens = long("prompt_tokens").default(0L)
    val completionTokens = long("completion_tokens").default(0L)
    val errorMessage = text("error_message").nullable()
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AiAnnotationResultsTable : Table("ai_annotation_results") {
    val id = uuid("id")
    val batchId = uuid("batch_id").references(AiAnnotationBatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val itemId = uuid("item_id").references(DataItemsTable.id, onDelete = ReferenceOption.CASCADE)
    val roundNo = integer("round_no")
    val status = varchar("status", 32)
    val result = jsonb("result").nullable()
    val acceptedResult = jsonb("accepted_result").nullable()
    val resultHash = varchar("result_hash", 64).nullable()
    val confidence = varchar("confidence", 16).nullable()
    val confidenceScore = decimal("confidence_score", 5, 4).nullable()
    val reason = text("reason").nullable()
    val needsHumanReview = bool("needs_human_review").default(false)
    val isSampled = bool("is_sampled").default(false)
    val riskFlags = jsonb("risk_flags").default("[]")
    val rawOutput = jsonb("raw_output").nullable()
    val errorMessage = text("error_message").nullable()
    val attemptCount = integer("attempt_count").default(0)
    val chunkNo = integer("chunk_no").nullable()
    val requestId = varchar("request_id", 80).nullable()
    val leasedAt = timestampWithTimeZone("leased_at").nullable()
    val leaseExpiresAt = timestampWithTimeZone("lease_expires_at").nullable()
    val reviewedBy = uuid("reviewed_by").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val reviewAction = varchar("review_action", 32).nullable()
    val reviewComment = text("review_comment").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(batchId, itemId, roundNo)
    }
}

object AnnotationTaskBatchesTable : Table("annotation_task_batches") {
    val id = uuid("id")
    val orderNo = varchar("order_no", 40).uniqueIndex()
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val annotatorId = uuid("annotator_id").references(UsersTable.id)
    val batchType = varchar("batch_type", 32).default("annotation")
    val status = varchar("status", 32)
    val totalCount = integer("total_count")
    val assignedAt = timestampWithTimeZone("assigned_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val dueAt = timestampWithTimeZone("due_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AnnotationTasksTable : Table("annotation_tasks") {
    val id = uuid("id")
    val batchId = uuid("batch_id").references(AnnotationTaskBatchesTable.id, onDelete = ReferenceOption.CASCADE)
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val itemId = uuid("item_id").references(DataItemsTable.id, onDelete = ReferenceOption.CASCADE)
    val annotatorId = uuid("annotator_id").references(UsersTable.id)
    val roundNo = integer("round_no")
    val status = varchar("status", 32)
    val assignedAt = timestampWithTimeZone("assigned_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val dueAt = timestampWithTimeZone("due_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(itemId, annotatorId, roundNo)
    }
}

object AnnotationsTable : Table("annotations") {
    val id = uuid("id")
    val taskId = uuid("task_id").references(AnnotationTasksTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val itemId = uuid("item_id").references(DataItemsTable.id, onDelete = ReferenceOption.CASCADE)
    val annotatorId = uuid("annotator_id").references(UsersTable.id)
    val roundNo = integer("round_no")
    val result = jsonb("result")
    val annotationType = varchar("annotation_type", 32).default("annotation")
    val reviewOfAnnotationId = uuid("review_of_annotation_id").nullable()
    val comment = text("comment").nullable()
    val isDisputed = bool("is_disputed")
    val status = varchar("status", 32)
    val adoptionStatus = short("adoption_status").default(0)
    val adoptedAt = timestampWithTimeZone("adopted_at").nullable()
    val adoptedBy = uuid("adopted_by").nullable()
    val adoptionComment = text("adoption_comment").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at")
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DatasetReviewsTable : Table("dataset_reviews") {
    val id = uuid("id")
    val datasetId = uuid("dataset_id").references(DatasetsTable.id, onDelete = ReferenceOption.CASCADE)
    val providerId = uuid("provider_id").references(UsersTable.id)
    val status = varchar("status", 32)
    val sampledItemCount = integer("sampled_item_count")
    val disputedItemCount = integer("disputed_item_count")
    val opinion = text("opinion").nullable()
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

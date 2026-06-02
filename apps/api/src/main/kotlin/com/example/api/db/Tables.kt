package com.example.api.db

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
    val finalResult = jsonb("final_result").nullable()
    val finalizedAt = timestampWithTimeZone("finalized_at").nullable()
    val finalizedBy = uuid("finalized_by").nullable()
    val status = varchar("status", 32)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
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
    val status = varchar("status", 32)
    val assignedAt = timestampWithTimeZone("assigned_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val dueAt = timestampWithTimeZone("due_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(itemId, annotatorId)
    }
}

object AnnotationsTable : Table("annotations") {
    val id = uuid("id")
    val taskId = uuid("task_id").references(AnnotationTasksTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val itemId = uuid("item_id").references(DataItemsTable.id, onDelete = ReferenceOption.CASCADE)
    val annotatorId = uuid("annotator_id").references(UsersTable.id)
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

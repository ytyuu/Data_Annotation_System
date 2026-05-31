package com.example.api.service.dataset

import com.example.api.db.DatasetsTable
import com.example.api.models.CreateDatasetRequest
import com.example.api.models.DatasetResponse
import com.example.api.service.auth.AuthResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 数据集业务服务，封装提供者数据集创建和查询逻辑。
 */
class DatasetService {
    private val objectMapper = ObjectMapper()

    /**
     * 为指定数据集提供者创建草稿数据集。
     *
     * @param providerId 数据集提供者用户 ID
     * @param request 创建数据集请求
     * @return 创建结果，成功时返回数据集响应数据
     */
    fun createProviderDataset(
        providerId: UUID,
        request: CreateDatasetRequest,
    ): AuthResult<DatasetResponse> {
        val name = request.name.trim()
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val annotationGuide = request.annotationGuide?.trim()?.takeIf { it.isNotEmpty() }
        val annotationSchema = request.annotationSchema.trim().ifEmpty { "{}" }
        val targetCompletionRatio = request.targetCompletionRatio.trim().ifEmpty { "50.00" }

        val validationError = validateDataset(name, annotationSchema, targetCompletionRatio)
        if (validationError != null) {
            return AuthResult.BadRequest(validationError)
        }

        val ratio = targetCompletionRatio.toBigDecimal()

        return try {
            val dataset = transaction {
                val now = OffsetDateTime.now()
                val datasetId = UUID.randomUUID()

                DatasetsTable.insert {
                    it[id] = datasetId
                    it[DatasetsTable.providerId] = providerId
                    it[DatasetsTable.name] = name
                    it[DatasetsTable.description] = description
                    it[DatasetsTable.annotationGuide] = annotationGuide
                    it[DatasetsTable.annotationSchema] = annotationSchema
                    it[status] = "draft"
                    it[DatasetsTable.targetCompletionRatio] = ratio
                    it[itemCount] = 0
                    it[completedItemCount] = 0
                    it[createdAt] = now
                    it[updatedAt] = now
                }

                DatasetResponse(
                    id = datasetId.toString(),
                    providerId = providerId.toString(),
                    name = name,
                    description = description,
                    annotationGuide = annotationGuide,
                    annotationSchema = annotationSchema,
                    status = "draft",
                    targetCompletionRatio = ratio.toPlainString(),
                    itemCount = 0,
                    completedItemCount = 0,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )
            }

            AuthResult.Success(dataset)
        } catch (error: ExposedSQLException) {
            AuthResult.BadRequest("数据集信息不符合数据库约束")
        }
    }

    /**
     * 查询指定数据集提供者创建的数据集列表。
     *
     * @param providerId 数据集提供者用户 ID
     * @return 查询结果，成功时返回按更新时间倒序排列的数据集列表
     */
    fun listProviderDatasets(providerId: UUID): AuthResult<List<DatasetResponse>> {
        val datasets = transaction {
            DatasetsTable
                .selectAll()
                .where { DatasetsTable.providerId eq providerId }
                .orderBy(DatasetsTable.updatedAt to SortOrder.DESC)
                .map { row ->
                    DatasetResponse(
                        id = row[DatasetsTable.id].toString(),
                        providerId = row[DatasetsTable.providerId].toString(),
                        name = row[DatasetsTable.name],
                        description = row[DatasetsTable.description],
                        annotationGuide = row[DatasetsTable.annotationGuide],
                        annotationSchema = row[DatasetsTable.annotationSchema],
                        status = row[DatasetsTable.status],
                        targetCompletionRatio = row[DatasetsTable.targetCompletionRatio].toPlainString(),
                        itemCount = row[DatasetsTable.itemCount],
                        completedItemCount = row[DatasetsTable.completedItemCount],
                        createdAt = row[DatasetsTable.createdAt].toString(),
                        updatedAt = row[DatasetsTable.updatedAt].toString(),
                    )
                }
        }

        return AuthResult.Success(datasets)
    }

    /**
     * 校验创建数据集请求中的业务字段。
     *
     * @param name 数据集名称
     * @param annotationSchema 标注结构 JSON 字符串
     * @param targetCompletionRatio 触发审核的目标完成比例
     * @return 校验失败时返回错误消息，校验通过返回 null
     */
    private fun validateDataset(
        name: String,
        annotationSchema: String,
        targetCompletionRatio: String,
    ): String? {
        val ratio = targetCompletionRatio.toBigDecimalOrNull()

        return when {
            name.length !in 1..120 -> "数据集名称长度必须为 1 到 120 个字符"
            !isJsonObject(annotationSchema) -> "标注结构必须是合法的 JSON 对象"
            ratio == null -> "目标完成比例必须是数字"
            ratio <= BigDecimal.ZERO || ratio > BigDecimal("100") -> "目标完成比例必须大于 0 且不超过 100"
            else -> null
        }
    }

    /**
     * 判断输入字符串是否为 JSON 对象。
     *
     * @param value 待校验的 JSON 字符串
     * @return 是 JSON 对象时返回 true
     */
    private fun isJsonObject(value: String): Boolean {
        return runCatching { objectMapper.readTree(value)?.isObject == true }.getOrDefault(false)
    }
}

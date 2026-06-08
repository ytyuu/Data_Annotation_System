package com.example.api.service.dataset

import com.example.api.db.DataItemsTable
import com.example.api.db.DatasetsTable
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 数据集查询工具函数，供 ProviderDatasetService 和 AnnotatorDatasetService 复用。
 */
object DatasetQueryHelper {
    private val objectMapper = ObjectMapper()

    /**
     * 查询每个数据集中已经形成最终结果的数据项数量。
     *
     * @param datasetIds 数据集 ID 列表
     * @return 数据集 ID 到已完成数据项数量的映射
     */
    fun countCompletedItems(datasetIds: List<UUID>): Map<UUID, Int> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        return DataItemsTable
            .select(DataItemsTable.datasetId)
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.status inList listOf("annotated", "accepted"))
            }
            .groupingBy { it[DataItemsTable.datasetId] }
            .eachCount()
    }

    /**
     * 比较两条标注结果是否一致。
     *
     * 比较策略：
     * 1. 优先提取 JSON 中 "value" 或 "values" 字段的选择值进行比对，
     *    忽略其他元数据差异（如坐标、时间戳等），适配选择型标注场景。
     * 2. 若不存在选择值字段，则回退到完整 JSON 对象逐字段比较。
     * 3. 若任一输入不是合法 JSON，则按字符串精确匹配。
     */
    fun areAnnotationResultsConsistent(left: String, right: String): Boolean {
        val leftNode = runCatching { objectMapper.readTree(left) }.getOrNull() ?: return left == right
        val rightNode = runCatching { objectMapper.readTree(right) }.getOrNull() ?: return left == right

        val leftValues = extractSelectionValues(leftNode)
        val rightValues = extractSelectionValues(rightNode)
        if (leftValues != null || rightValues != null) {
            return leftValues == rightValues
        }

        return leftNode == rightNode
    }

    /**
     * 查询每个数据集中处于争议状态的数据项数量。
     *
     * @param datasetIds 数据集 ID 列表
     * @return 数据集 ID 到争议数据项数量的映射
     */
    fun countDisputedItems(datasetIds: List<UUID>): Map<UUID, Int> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        return DataItemsTable
            .select(DataItemsTable.datasetId)
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (DataItemsTable.status eq "disputed")
            }
            .groupingBy { it[DataItemsTable.datasetId] }
            .eachCount()
    }

    /**
     * 检查数据集是否达到目标完成比例，若达到且当前仍处于进行中，
     * 则自动过渡到 `reviewing` 状态，进入审核阶段。
     *
     * 应在 [refreshDatasetCompletedItemCount] 之后调用。
     */
    fun checkAndTransitionToReviewing(datasetId: UUID, now: OffsetDateTime = OffsetDateTime.now()) {
        val dataset = DatasetsTable
            .selectAll()
            .where { DatasetsTable.id eq datasetId }
            .limit(1)
            .firstOrNull() ?: return

        if (dataset[DatasetsTable.status] != "in_progress") return

        val itemCount = dataset[DatasetsTable.itemCount]
        if (itemCount <= 0) return

        val completedCount = dataset[DatasetsTable.completedItemCount]
        val targetRatio = dataset[DatasetsTable.targetCompletionRatio]

        val actualPercent = completedCount.toDouble() / itemCount.toDouble() * 100.0
        if (actualPercent >= targetRatio.toDouble()) {
            DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
                it[status] = "reviewing"
                it[updatedAt] = now
            }
        }
    }

    /**
     * 重新计算并写回指定数据集的已完成数据项数量。
     *
     * 数据项状态为 `annotated` 或 `accepted` 时视为已完成，
     * 用于前端展示数据集进度和判断数据集是否达到目标完成比例。
     */
    fun refreshDatasetCompletedItemCount(datasetId: UUID, now: OffsetDateTime = OffsetDateTime.now()): Int {
        val completedCount = DataItemsTable
            .selectAll()
            .where {
                (DataItemsTable.datasetId eq datasetId) and
                    (DataItemsTable.status inList listOf("annotated", "accepted"))
            }
            .count()
            .toInt()

        DatasetsTable.update({ DatasetsTable.id eq datasetId }) {
            it[completedItemCount] = completedCount
            it[updatedAt] = now
        }

        return completedCount
    }

    /**
     * 从标注结果 JSON 中提取选择值，用于一致性比对。
     *
     * 支持 "value"（单选）和 "values"（多选）两种字段格式，
     * 同时提取 "subValues" 中的子选项值参与比对。
     * 多选时返回已排序的字符串列表，便于比较时忽略顺序差异。
     */
    private fun extractSelectionValues(node: JsonNode): List<String>? {
        val subValuesNode = node.get("subValues")

        val valueNode = node.get("value")
        if (valueNode?.isTextual == true) {
            val result = mutableListOf(valueNode.asText())
            val subArray = subValuesNode?.get(valueNode.asText())
            if (subArray?.isArray == true) {
                subArray
                    .mapNotNull { if (it.isTextual) it.asText() else null }
                    .sorted()
                    .forEach { result.add(it) }
            }
            return result
        }

        val valuesNode = node.get("values")
        if (valuesNode?.isArray == true) {
            val result = mutableListOf<String>()
            val mainValues = valuesNode
                .mapNotNull { if (it.isTextual) it.asText() else null }
                .sorted()
            result.addAll(mainValues)
            if (subValuesNode?.isObject == true) {
                mainValues.forEach { key ->
                    val subArray = subValuesNode.get(key)
                    if (subArray?.isArray == true) {
                        subArray
                            .mapNotNull { if (it.isTextual) it.asText() else null }
                            .sorted()
                            .forEach { result.add(it) }
                    }
                }
            }
            return result
        }

        return null
    }
}

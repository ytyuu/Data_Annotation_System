# 数据标注系统后端扩展重构方案

## 1. 背景与目标

### 1.1 当前现状

系统当前以**文本分类标注**为主，后端通过 `DatasetQueryHelper.areAnnotationResultsConsistent` 进行标注一致性比对，仅支持提取 `value`/`values` 字段进行简单列表比较。

### 1.2 扩展需求

未来可能需要支持多种标注类型：
- **图片目标检测**（Bounding Box）：需要 IoU 交并比计算
- **图片语义分割**（Polygon）：需要多边形几何相似度计算
- **文本实体识别**（NER）：需要实体重叠率计算
- **音频时序标注**：需要时间区间重叠计算
- **视频行为标注**：需要时空一致性计算

### 1.3 核心目标

- **API 通用性**：不随标注类型增加而新增 API 端点
- **可扩展性**：新增标注类型时，后端改动最小化
- **可维护性**：业务逻辑与标注类型解耦

---

## 2. 设计原则

### 2.1 数据库层：保持通用

```sql
-- data_items.content_type 已支持 text/image/audio/video/json
-- annotations.result 为 JSONB，可存任意结构化结果
-- datasets.annotation_schema 为 JSONB，可描述任意标注配置
```

**原则**：数据库表结构不随标注类型变化，所有类型差异通过 JSONB 字段承载。

### 2.2 API 层：保持统一

所有现有 API 端点保持不变：

| API | 说明 |
|-----|------|
| `POST /api/provider/datasets` | 创建数据集（schema 字段自描述） |
| `POST /api/annotator/datasets/{id}/claim` | 领取任务 |
| `GET /api/annotator/task-batches/{id}/workspace` | 获取工作台 |
| `POST /api/annotator/task-batches/{id}/submit` | 提交标注（result 为 JSONB） |
| `POST /api/provider/datasets/{id}/items/{itemId}/resolve-dispute` | 裁决争议 |

**原则**：通过 `annotation_schema` 的内容区分标注类型，而非通过不同 API。

### 2.3 业务逻辑层：策略模式

将标注类型相关的一致性比对逻辑抽象为**策略接口**，每种标注类型实现独立策略。

---

## 3. 重构方案

### 3.1 新增标注类型枚举

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/AnnotationType.kt`

```kotlin
package com.example.api.service.dataset

/**
 * 支持的标注类型枚举。
 *
 * 新增标注类型时，需要：
 * 1. 在此枚举中添加新类型
 * 2. 实现对应的 ConsistencyStrategy
 * 3. 注册到 ConsistencyStrategyFactory
 */
enum class AnnotationType(val typeName: String) {
    CLASSIFICATION("classification"),    // 分类标注（当前）
    BOUNDING_BOX("bounding_box"),        // 目标检测
    POLYGON("polygon"),                  // 语义分割
    NER("ner"),                          // 命名实体识别
    TRANSCRIPTION("transcription"),      // 音频转写
    SEGMENTATION("segmentation");        // 时序分割

    companion object {
        fun fromSchemaType(type: String?): AnnotationType {
            return values().find { it.typeName == type }
                ?: CLASSIFICATION  // 默认回退到分类
        }
    }
}
```

### 3.2 新增一致性比对策略接口

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/consistency/AnnotationConsistencyStrategy.kt`

```kotlin
package com.example.api.service.dataset.consistency

import com.example.api.service.dataset.AnnotationType
import com.fasterxml.jackson.databind.JsonNode

/**
 * 标注结果一致性比对策略接口。
 *
 * 每种标注类型需要实现此接口，定义如何比对两条标注结果是否一致。
 * 一致性标准由业务决定，例如：
 * - 分类标注：选择值完全相同
 * - 目标检测：IoU > 0.5
 * - NER：实体重叠率 > 0.8
 */
interface AnnotationConsistencyStrategy {
    /**
     * 返回此策略支持的标注类型。
     */
    fun getType(): AnnotationType

    /**
     * 比对两条标注结果是否一致。
     *
     * @param left 第一条标注结果的 JSON 节点
     * @param right 第二条标注结果的 JSON 节点
     * @return 是否一致
     */
    fun areConsistent(left: JsonNode, right: JsonNode): Boolean

    /**
     * 计算一致性分数（0.0 - 1.0），用于展示或阈值判断。
     *
     * @param left 第一条标注结果的 JSON 节点
     * @param right 第二条标注结果的 JSON 节点
     * @return 一致性分数
     */
    fun calculateConsistencyScore(left: JsonNode, right: JsonNode): Double {
        return if (areConsistent(left, right)) 1.0 else 0.0
    }
}
```

### 3.3 实现分类标注策略（迁移现有逻辑）

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/consistency/ClassificationConsistencyStrategy.kt`

```kotlin
package com.example.api.service.dataset.consistency

import com.example.api.service.dataset.AnnotationType
import com.fasterxml.jackson.databind.JsonNode

/**
 * 分类标注一致性比对策略。
 *
 * 比对逻辑：
 * 1. 单选：比较 "value" 字段是否相同
 * 2. 多选：比较 "values" 数组是否相同（忽略顺序）
 * 3. 回退：完整 JSON 对象比较
 */
class ClassificationConsistencyStrategy : AnnotationConsistencyStrategy {

    override fun getType(): AnnotationType = AnnotationType.CLASSIFICATION

    override fun areConsistent(left: JsonNode, right: JsonNode): Boolean {
        val leftValues = extractSelectionValues(left)
        val rightValues = extractSelectionValues(right)

        // 如果至少一方有选择值字段，按选择值比较
        if (leftValues != null || rightValues != null) {
            return leftValues == rightValues
        }

        // 回退到完整 JSON 比较
        return left == right
    }

    override fun calculateConsistencyScore(left: JsonNode, right: JsonNode): Double {
        val leftValues = extractSelectionValues(left)
        val rightValues = extractSelectionValues(right)

        if (leftValues == null || rightValues == null) {
            return if (left == right) 1.0 else 0.0
        }

        // 计算 Jaccard 相似度
        val leftSet = leftValues.toSet()
        val rightSet = rightValues.toSet()
        val intersection = leftSet.intersect(rightSet).size.toDouble()
        val union = leftSet.union(rightSet).size.toDouble()

        return if (union == 0.0) 1.0 else intersection / union
    }

    private fun extractSelectionValues(node: JsonNode): List<String>? {
        val valueNode = node.get("value")
        if (valueNode?.isTextual == true) {
            return listOf(valueNode.asText())
        }

        val valuesNode = node.get("values")
        if (valuesNode?.isArray == true) {
            return valuesNode
                .mapNotNull { if (it.isTextual) it.asText() else null }
                .sorted()
        }

        return null
    }
}
```

### 3.4 实现目标检测策略（示例）

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/consistency/BoundingBoxConsistencyStrategy.kt`

```kotlin
package com.example.api.service.dataset.consistency

import com.example.api.service.dataset.AnnotationType
import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.max
import kotlin.math.min

/**
 * 目标检测（Bounding Box）一致性比对策略。
 *
 * 比对逻辑：
 * 1. 比较检测框数量是否相同
 * 2. 为每个框计算 IoU（交并比）
 * 3. 平均 IoU > 阈值（默认 0.5）视为一致
 *
 * 结果格式示例：
 * ```json
 * {
 *   "boxes": [
 *     {"x": 10, "y": 20, "w": 100, "h": 80, "label": "cat", "confidence": 0.95},
 *     {"x": 150, "y": 200, "w": 50, "h": 60, "label": "dog", "confidence": 0.87}
 *   ]
 * }
 * ```
 */
class BoundingBoxConsistencyStrategy(
    private val iouThreshold: Double = 0.5
) : AnnotationConsistencyStrategy {

    override fun getType(): AnnotationType = AnnotationType.BOUNDING_BOX

    override fun areConsistent(left: JsonNode, right: JsonNode): Boolean {
        return calculateConsistencyScore(left, right) >= iouThreshold
    }

    override fun calculateConsistencyScore(left: JsonNode, right: JsonNode): Double {
        val leftBoxes = left.get("boxes")?.toList() ?: return 0.0
        val rightBoxes = right.get("boxes")?.toList() ?: return 0.0

        if (leftBoxes.isEmpty() && rightBoxes.isEmpty()) return 1.0
        if (leftBoxes.isEmpty() || rightBoxes.isEmpty()) return 0.0

        // 为每个左框找到最佳匹配的右框
        val matchedIous = mutableListOf<Double>()
        val usedRightIndices = mutableSetOf<Int>()

        for (leftBox in leftBoxes) {
            var bestIou = 0.0
            var bestRightIndex = -1

            for ((index, rightBox) in rightBoxes.withIndex()) {
                if (index in usedRightIndices) continue

                val iou = calculateIoU(leftBox, rightBox)
                if (iou > bestIou) {
                    bestIou = iou
                    bestRightIndex = index
                }
            }

            if (bestRightIndex >= 0) {
                matchedIous.add(bestIou)
                usedRightIndices.add(bestRightIndex)
            }
        }

        // 计算平均 IoU
        return if (matchedIous.isEmpty()) 0.0
        else matchedIous.average()
    }

    private fun calculateIoU(left: JsonNode, right: JsonNode): Double {
        val leftX = left.get("x")?.asDouble() ?: return 0.0
        val leftY = left.get("y")?.asDouble() ?: return 0.0
        val leftW = left.get("w")?.asDouble() ?: return 0.0
        val leftH = left.get("h")?.asDouble() ?: return 0.0

        val rightX = right.get("x")?.asDouble() ?: return 0.0
        val rightY = right.get("y")?.asDouble() ?: return 0.0
        val rightW = right.get("w")?.asDouble() ?: return 0.0
        val rightH = right.get("h")?.asDouble() ?: return 0.0

        // 计算交集
        val xLeft = max(leftX, rightX)
        val yTop = max(leftY, rightY)
        val xRight = min(leftX + leftW, rightX + rightW)
        val yBottom = min(leftY + leftH, rightY + rightH)

        if (xRight <= xLeft || yBottom <= yTop) return 0.0

        val intersection = (xRight - xLeft) * (yBottom - yTop)
        val leftArea = leftW * leftH
        val rightArea = rightW * rightH
        val union = leftArea + rightArea - intersection

        return if (union <= 0) 0.0 else intersection / union
    }
}
```

### 3.5 实现 NER 策略（示例）

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/consistency/NERConsistencyStrategy.kt`

```kotlin
package com.example.api.service.dataset.consistency

import com.example.api.service.dataset.AnnotationType
import com.fasterxml.jackson.databind.JsonNode

/**
 * 命名实体识别（NER）一致性比对策略。
 *
 * 比对逻辑：
 * 1. 提取实体列表（text, start, end, label）
 * 2. 比较实体标签是否相同
 * 3. 计算文本重叠率
 * 4. 平均重叠率 > 阈值（默认 0.8）视为一致
 *
 * 结果格式示例：
 * ```json
 * {
 *   "entities": [
 *     {"text": "北京", "start": 0, "end": 2, "label": "LOC"},
 *     {"text": "张三", "start": 5, "end": 7, "label": "PER"}
 *   ]
 * }
 * ```
 */
class NERConsistencyStrategy(
    private val overlapThreshold: Double = 0.8
) : AnnotationConsistencyStrategy {

    override fun getType(): AnnotationType = AnnotationType.NER

    override fun areConsistent(left: JsonNode, right: JsonNode): Boolean {
        return calculateConsistencyScore(left, right) >= overlapThreshold
    }

    override fun calculateConsistencyScore(left: JsonNode, right: JsonNode): Double {
        val leftEntities = extractEntities(left)
        val rightEntities = extractEntities(right)

        if (leftEntities.isEmpty() && rightEntities.isEmpty()) return 1.0
        if (leftEntities.isEmpty() || rightEntities.isEmpty()) return 0.0

        // 为每个左实体找到最佳匹配的右实体
        val matchedScores = mutableListOf<Double>()
        val usedRightIndices = mutableSetOf<Int>()

        for (leftEntity in leftEntities) {
            var bestScore = 0.0
            var bestRightIndex = -1

            for ((index, rightEntity) in rightEntities.withIndex()) {
                if (index in usedRightIndices) continue

                val score = calculateEntityMatchScore(leftEntity, rightEntity)
                if (score > bestScore) {
                    bestScore = score
                    bestRightIndex = index
                }
            }

            if (bestRightIndex >= 0) {
                matchedScores.add(bestScore)
                usedRightIndices.add(bestRightIndex)
            }
        }

        return if (matchedScores.isEmpty()) 0.0
        else matchedScores.average()
    }

    private fun extractEntities(node: JsonNode): List<Entity> {
        return node.get("entities")?.mapNotNull { entityNode ->
            try {
                Entity(
                    text = entityNode.get("text")?.asText() ?: return@mapNotNull null,
                    start = entityNode.get("start")?.asInt() ?: return@mapNotNull null,
                    end = entityNode.get("end")?.asInt() ?: return@mapNotNull null,
                    label = entityNode.get("label")?.asText() ?: return@mapNotNull null
                )
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    private fun calculateEntityMatchScore(left: Entity, right: Entity): Double {
        // 标签必须相同
        if (left.label != right.label) return 0.0

        // 计算文本位置重叠率
        val overlapStart = maxOf(left.start, right.start)
        val overlapEnd = minOf(left.end, right.end)

        if (overlapEnd <= overlapStart) return 0.0

        val overlapLength = overlapEnd - overlapStart
        val leftLength = left.end - left.start
        val rightLength = right.end - right.start

        // 使用 F1 风格的平均
        val precision = overlapLength.toDouble() / rightLength
        val recall = overlapLength.toDouble() / leftLength

        return if (precision + recall == 0.0) 0.0
        else 2 * precision * recall / (precision + recall)
    }

    data class Entity(
        val text: String,
        val start: Int,
        val end: Int,
        val label: String
    )
}
```

### 3.6 策略工厂

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/consistency/ConsistencyStrategyFactory.kt`

```kotlin
package com.example.api.service.dataset.consistency

import com.example.api.service.dataset.AnnotationType
import com.fasterxml.jackson.databind.JsonNode

/**
 * 一致性比对策略工厂。
 *
 * 负责管理所有标注类型的比对策略，并提供根据 schema 获取策略的方法。
 *
 * 新增标注类型时：
 * 1. 在 strategies map 中注册新策略
 * 2. 无需修改此工厂的其他代码
 */
object ConsistencyStrategyFactory {

    private val strategies: Map<AnnotationType, AnnotationConsistencyStrategy> = mapOf(
        AnnotationType.CLASSIFICATION to ClassificationConsistencyStrategy(),
        AnnotationType.BOUNDING_BOX to BoundingBoxConsistencyStrategy(),
        AnnotationType.NER to NERConsistencyStrategy(),
        // 新增标注类型时在此注册...
    )

    /**
     * 根据标注类型获取对应策略。
     */
    fun getStrategy(type: AnnotationType): AnnotationConsistencyStrategy {
        return strategies[type]
            ?: throw IllegalArgumentException(
                "不支持的标注类型: $type。请在 ConsistencyStrategyFactory 中注册对应策略。"
            )
    }

    /**
     * 从 schema JSON 节点自动识别标注类型并获取策略。
     */
    fun getStrategyFromSchema(schema: JsonNode?): AnnotationConsistencyStrategy {
        if (schema == null) {
            return getStrategy(AnnotationType.CLASSIFICATION)
        }

        val typeStr = schema.get("type")?.asText()
        val type = AnnotationType.fromSchemaType(typeStr)
        return getStrategy(type)
    }

    /**
     * 从 schema JSON 字符串自动识别标注类型并获取策略。
     */
    fun getStrategyFromSchema(schemaJson: String?): AnnotationConsistencyStrategy {
        if (schemaJson.isNullOrBlank()) {
            return getStrategy(AnnotationType.CLASSIFICATION)
        }

        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val schema = objectMapper.readTree(schemaJson)
            getStrategyFromSchema(schema)
        } catch (e: Exception) {
            getStrategy(AnnotationType.CLASSIFICATION)
        }
    }

    /**
     * 获取所有已注册的策略类型（用于管理界面展示）。
     */
    fun getRegisteredTypes(): List<AnnotationType> {
        return strategies.keys.toList()
    }
}
```

### 3.7 改造 DatasetQueryHelper

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/DatasetQueryHelper.kt`

```kotlin
package com.example.api.service.dataset

import com.example.api.db.DataItemsTable
import com.example.api.db.DatasetsTable
import com.example.api.service.dataset.consistency.ConsistencyStrategyFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 数据集查询工具函数。
 *
 * 改造后的一致性比对通过策略模式实现，支持多种标注类型。
 */
object DatasetQueryHelper {
    private val objectMapper = ObjectMapper()

    /**
     * 比较两条标注结果是否一致。
     *
     * 改造后：
     * 1. 优先根据 schema 确定标注类型
     * 2. 使用对应策略进行比对
     * 3. 无 schema 时回退到通用比较
     *
     * @param left 第一条标注结果 JSON 字符串
     * @param right 第二条标注结果 JSON 字符串
     * @param schemaJson 数据集标注 schema JSON 字符串（可选，用于确定类型）
     * @return 是否一致
     */
    fun areAnnotationResultsConsistent(
        left: String,
        right: String,
        schemaJson: String? = null
    ): Boolean {
        val leftNode = runCatching { objectMapper.readTree(left) }.getOrNull()
            ?: return left == right
        val rightNode = runCatching { objectMapper.readTree(right) }.getOrNull()
            ?: return left == right

        // 如果有 schema，使用对应策略
        if (!schemaJson.isNullOrBlank()) {
            try {
                val strategy = ConsistencyStrategyFactory.getStrategyFromSchema(schemaJson)
                return strategy.areConsistent(leftNode, rightNode)
            } catch (e: Exception) {
                // 策略执行失败时回退到通用比较
                // 记录日志...
            }
        }

        // 回退到完整 JSON 比较（向后兼容）
        return leftNode == rightNode
    }

    /**
     * 计算两条标注结果的一致性分数。
     *
     * @param left 第一条标注结果 JSON 字符串
     * @param right 第二条标注结果 JSON 字符串
     * @param schemaJson 数据集标注 schema JSON 字符串
     * @return 一致性分数（0.0 - 1.0）
     */
    fun calculateConsistencyScore(
        left: String,
        right: String,
        schemaJson: String? = null
    ): Double {
        val leftNode = runCatching { objectMapper.readTree(left) }.getOrNull()
            ?: return if (left == right) 1.0 else 0.0
        val rightNode = runCatching { objectMapper.readTree(right) }.getOrNull()
            ?: return if (left == right) 1.0 else 0.0

        if (!schemaJson.isNullOrBlank()) {
            try {
                val strategy = ConsistencyStrategyFactory.getStrategyFromSchema(schemaJson)
                return strategy.calculateConsistencyScore(leftNode, rightNode)
            } catch (e: Exception) {
                // 回退
            }
        }

        return if (leftNode == rightNode) 1.0 else 0.0
    }

    // ... 其他方法保持不变 ...
}
```

### 3.8 改造 AnnotatorDatasetService.finalizeReviewedItem

**文件**：`apps/api/src/main/kotlin/com/example/api/service/dataset/AnnotatorDatasetService.kt`

```kotlin
/**
 * 原始标注与互查均提交后，推进数据项的最终状态。
 *
 * 改造后：
 * - 从数据集获取 schema
 * - 使用策略模式进行一致性比对
 * - 状态推进逻辑与标注类型无关，保持不变
 */
private fun finalizeReviewedItem(
    datasetId: UUID,
    itemId: UUID,
    originalAnnotationId: UUID,
    now: OffsetDateTime,
) {
    val annotations = AnnotationsTable
        .selectAll()
        .where {
            (AnnotationsTable.itemId eq itemId) and
                (AnnotationsTable.annotationType inList listOf("annotation", "review"))
        }
        .toList()

    val originalAnnotation = annotations.firstOrNull { 
        it[AnnotationsTable.id] == originalAnnotationId 
    } ?: return
    val reviewAnnotation = annotations.firstOrNull { 
        it[AnnotationsTable.annotationType] == "review" 
    } ?: return

    // 获取数据集 schema
    val dataset = DatasetsTable
        .selectAll()
        .where { DatasetsTable.id eq datasetId }
        .firstOrNull()
    val schemaJson = dataset?.get(DatasetsTable.annotationSchema)

    // 使用策略进行一致性比对
    val isDisputed = originalAnnotation[AnnotationsTable.isDisputed] ||
        reviewAnnotation[AnnotationsTable.isDisputed] ||
        !DatasetQueryHelper.areAnnotationResultsConsistent(
            originalAnnotation[AnnotationsTable.result],
            reviewAnnotation[AnnotationsTable.result],
            schemaJson  // 传入 schema
        )

    if (isDisputed) {
        // ... 争议处理逻辑保持不变 ...
    } else {
        // ... 采纳处理逻辑保持不变 ...
    }

    DatasetQueryHelper.refreshDatasetCompletedItemCount(datasetId, now)
}
```

---

## 4. 扩展指南

### 4.1 新增标注类型的完整步骤

以**音频时序标注**为例：

#### 步骤 1：定义结果格式

```json
{
  "type": "segmentation",
  "version": 1,
  "categories": [
    { "id": "speech", "name": "语音", "color": "#00FF00" },
    { "id": "music", "name": "音乐", "color": "#0000FF" },
    { "id": "noise", "name": "噪音", "color": "#FF0000" }
  ]
}
```

标注结果示例：

```json
{
  "segments": [
    {"start": 0.0, "end": 3.5, "label": "speech"},
    {"start": 4.0, "end": 8.2, "label": "music"}
  ]
}
```

#### 步骤 2：新增枚举值

```kotlin
// AnnotationType.kt
enum class AnnotationType(val typeName: String) {
    // ... 已有类型 ...
    SEGMENTATION("segmentation");  // 新增
}
```

#### 步骤 3：实现策略

```kotlin
// consistency/SegmentationConsistencyStrategy.kt
package com.example.api.service.dataset.consistency

class SegmentationConsistencyStrategy(
    private val overlapThreshold: Double = 0.7
) : AnnotationConsistencyStrategy {

    override fun getType(): AnnotationType = AnnotationType.SEGMENTATION

    override fun areConsistent(left: JsonNode, right: JsonNode): Boolean {
        return calculateConsistencyScore(left, right) >= overlapThreshold
    }

    override fun calculateConsistencyScore(left: JsonNode, right: JsonNode): Double {
        val leftSegments = extractSegments(left)
        val rightSegments = extractSegments(right)
        
        // 计算时序重叠率...
        // 实现细节省略
        
        return averageOverlap
    }
}
```

#### 步骤 4：注册策略

```kotlin
// ConsistencyStrategyFactory.kt
private val strategies: Map<AnnotationType, AnnotationConsistencyStrategy> = mapOf(
    // ... 已有策略 ...
    AnnotationType.SEGMENTATION to SegmentationConsistencyStrategy(),  // 新增
)
```

#### 步骤 5：前端扩展（参考 AGENTS.md）

- `DataItemViewer`：增加 `audio` 分支
- `AnnotationEditor`：增加 `segmentation` 类型分支
- `AnnotationResultViewer`：增加解析 `segments` 格式

**后端总工作量**：新增 1 个枚举值 + 1 个策略类 + 1 行注册代码 = **约 100 行代码**

---

## 5. 目录结构

重构后的后端目录结构：

```
apps/api/src/main/kotlin/com/example/api/
├── service/
│   └── dataset/
│       ├── AnnotatorDatasetService.kt          # 标注员服务（改造后）
│       ├── ProviderDatasetService.kt           # 提供者服务（不变）
│       ├── DatasetQueryHelper.kt               # 查询工具（改造后）
│       ├── AnnotationType.kt                   # 【新增】标注类型枚举
│       └── consistency/                        # 【新增】一致性策略包
│           ├── AnnotationConsistencyStrategy.kt # 策略接口
│           ├── ConsistencyStrategyFactory.kt    # 策略工厂
│           ├── ClassificationConsistencyStrategy.kt # 分类策略
│           ├── BoundingBoxConsistencyStrategy.kt    # 目标检测策略
│           ├── NERConsistencyStrategy.kt            # NER 策略
│           └── SegmentationConsistency.kt           # 时序分割策略（示例）
```

---

## 6. 向后兼容性

### 6.1 现有数据兼容

- 无 `type` 字段的 schema 默认回退到 `CLASSIFICATION`
- `DatasetQueryHelper.areAnnotationResultsConsistent` 新增可选参数 `schemaJson`，不传时行为不变

### 6.2 API 兼容

- 所有现有 API 端点 URL、请求参数、响应格式**完全不变**
- 前端无需修改即可继续使用现有功能

---

## 7. 测试建议

### 7.1 策略单元测试

每种策略需要测试：
- 完全一致的结果
- 部分一致的结果
- 完全不一致的结果
- 边界情况（空结果、缺失字段等）

### 7.2 集成测试

- 不同 schema 类型的一致性比对流程
- 争议处理流程
- 状态机推进流程

---

## 8. 总结

| 层面 | 改造内容 | 工作量 |
|------|---------|--------|
| **数据库** | 无需改动 | ⭐ |
| **API 接口** | 无需新增 | ⭐ |
| **策略接口** | 新增 1 个接口 + 1 个工厂 | ⭐⭐ |
| **分类策略** | 提取现有逻辑 | ⭐⭐ |
| **新类型策略** | 每种类型 1 个类（约 100 行） | ⭐⭐ |
| **服务改造** | 传入 schema 参数 | ⭐ |

**核心收益**：
- 新增标注类型时，后端只需新增 1 个策略类
- API 保持通用，无需随类型增加而膨胀
- 业务逻辑与标注类型完全解耦
- 易于测试和维护

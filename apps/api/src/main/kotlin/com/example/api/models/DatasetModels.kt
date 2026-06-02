package com.example.api.models

/**
 * 创建数据集请求数据。
 *
 * @property name 数据集名称
 * @property description 数据集描述
 * @property annotationGuide 标注说明或标注文档
 * @property annotationSchema 标注结构 JSON 字符串
 * @property targetCompletionRatio 触发审核的目标完成比例
 */
data class CreateDatasetRequest(
    val name: String = "",
    val description: String? = null,
    val annotationGuide: String? = null,
    val annotationSchema: String = "{}",
    val targetCompletionRatio: String = "50.00",
)

/**
 * 数据集响应数据。
 *
 * @property id 数据集 ID
 * @property providerId 数据集提供者用户 ID
 * @property name 数据集名称
 * @property description 数据集描述
 * @property annotationGuide 标注说明或标注文档
 * @property annotationSchema 标注结构 JSON 字符串
 * @property status 数据集状态
 * @property targetCompletionRatio 触发审核的目标完成比例
 * @property itemCount 数据项总数
 * @property completedItemCount 已完成数据项数量
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 * @property canClaim 是否可继续领取任务（标注员场景）
 * @property pendingItemCount 数据集中待标注数据项数量
 * @property reviewableItemCount 数据集中可互查数据项数量
 */
data class DatasetResponse(
    val id: String,
    val providerId: String,
    val name: String,
    val description: String?,
    val annotationGuide: String?,
    val annotationSchema: String,
    val status: String,
    val targetCompletionRatio: String,
    val itemCount: Int,
    val completedItemCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val canClaim: Boolean? = null,
    val pendingItemCount: Int? = null,
    val reviewableItemCount: Int? = null,
)

/**
 * 批量导入数据项请求数据。
 *
 * @property items 待导入的数据项列表
 */
data class ImportDataItemsRequest(
    val items: List<DataItemInput> = emptyList(),
)

/**
 * 单条数据项导入输入。
 *
 * @property content 数据内容或资源地址
 * @property contentType 内容类型，当前前端默认使用 text
 * @property metadata 数据项扩展信息 JSON 字符串
 */
data class DataItemInput(
    val content: String = "",
    val contentType: String = "text",
    val metadata: String = "{}",
)

/**
 * 批量导入数据项响应数据。
 *
 * @property importedCount 本次成功导入的数据项数量
 * @property itemCount 导入后数据集的数据项总数
 */
data class ImportDataItemsResponse(
    val importedCount: Int,
    val itemCount: Int,
)

/**
 * 数据项响应数据。
 *
 * @property id 数据项 ID
 * @property datasetId 所属数据集 ID
 * @property content 数据内容或资源地址
 * @property contentType 内容类型
 * @property metadata 数据项扩展信息 JSON 字符串
 * @property finalResult 争议裁决后的最终标注结果 JSON 字符串
 * @property finalizedAt 最终结果确认时间
 * @property finalizedBy 确认最终结果的提供方用户 ID
 * @property status 数据项状态
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
data class DataItemResponse(
    val id: String,
    val datasetId: String,
    val content: String,
    val contentType: String,
    val metadata: String,
    val finalResult: String? = null,
    val finalizedAt: String? = null,
    val finalizedBy: String? = null,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * 提供者处理争议数据项请求。
 *
 * @property finalResult 提供者确认的最终标注结果 JSON 字符串
 * @property comment 采纳/拒绝说明
 */
data class ResolveDisputeRequest(
    val finalResult: String = "{}",
    val comment: String? = null,
)

/**
 * 删除数据项响应数据。
 *
 * @property message 删除结果消息
 * @property itemCount 删除后数据集的数据项总数
 */
data class DeleteDataItemResponse(
    val message: String,
    val itemCount: Int,
)

/**
 * 更新数据集请求数据。
 *
 * @property name 数据集名称
 * @property description 数据集描述
 * @property annotationGuide 标注说明或标注文档
 * @property annotationSchema 标注结构 JSON 字符串
 * @property targetCompletionRatio 触发审核的目标完成比例
 */
data class UpdateDatasetRequest(
    val name: String = "",
    val description: String? = null,
    val annotationGuide: String? = null,
    val annotationSchema: String = "{}",
    val targetCompletionRatio: String = "50.00",
)

/**
 * 更新数据集响应数据。
 *
 * @property message 更新结果消息
 */
data class UpdateDatasetResponse(
    val message: String,
)

/**
 * 删除数据集响应数据。
 *
 * @property message 删除结果消息
 */
data class DeleteDatasetResponse(
    val message: String,
)

/**
 * 发布数据集响应数据。
 *
 * @property message 发布结果消息
 * @property status 发布后的数据集状态
 */
data class PublishDatasetResponse(
    val message: String,
    val status: String,
)

/**
 * 标注员领取任务请求数据。
 *
 * @property count 期望领取的任务数量
 * @property taskType 任务类别：`annotation` 标注任务，`review` 互查任务
 */
data class ClaimTasksRequest(
    val count: Int = 1,
    val taskType: String = "annotation",
)

/**
 * 单条任务分配响应数据。
 *
 * @property taskId 标注任务项 ID
 * @property item 数据项信息
 */
data class TaskAssignmentResponse(
    val taskId: String,
    val item: DataItemResponse,
)

/**
 * 标注员领取任务响应数据。
 *
 * @property batchId 任务单 ID
 * @property orderNo 任务单号
 * @property datasetId 数据集 ID
 * @property assignedCount 实际领取到的任务数量
 * @property tasks 领取到的任务列表
 */
data class ClaimTasksResponse(
    val batchId: String,
    val orderNo: String,
    val datasetId: String,
    val assignedCount: Int,
    val tasks: List<TaskAssignmentResponse>,
)

/**
 * 标注员任务单响应数据。
 *
 * @property batchId 任务单 ID
 * @property orderNo 任务单号
 * @property datasetId 数据集 ID
 * @property datasetName 数据集名称
 * @property status 任务单状态
 * @property totalCount 任务总数
 * @property assignedCount 已分配数量
 * @property inProgressCount 进行中数量
 * @property submittedCount 已提交数量
 * @property assignedAt 领取时间
 * @property startedAt 开始时间
 * @property submittedAt 提交时间
 */
data class AnnotatorTaskResponse(
    val batchId: String,
    val orderNo: String,
    val datasetId: String,
    val datasetName: String,
    val status: String,
    val totalCount: Int,
    val assignedCount: Int,
    val inProgressCount: Int,
    val submittedCount: Int,
    val assignedAt: String,
    val startedAt: String?,
    val submittedAt: String?,
)

/**
 * 标注员单条任务详情响应数据（含数据项内容）。
 *
 * @property batchId 任务单 ID
 * @property orderNo 任务单号
 * @property taskId 标注任务项 ID
 * @property item 数据项信息
 * @property status 任务状态
 * @property assignedAt 分配时间
 * @property startedAt 开始时间
 * @property submittedAt 提交时间
 * @property annotationResult 已提交的标注结果 JSON 字符串
 * @property annotationIsDisputed 是否存在争议
 */
data class AnnotatorTaskDetailResponse(
    val batchId: String,
    val orderNo: String,
    val taskId: String,
    val item: DataItemResponse,
    val status: String,
    val assignedAt: String,
    val startedAt: String?,
    val submittedAt: String?,
    val annotationResult: String? = null,
    val annotationIsDisputed: Boolean? = null,
)

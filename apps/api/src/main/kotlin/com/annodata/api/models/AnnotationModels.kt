package com.annodata.api.models

/**
 * 标注员任务单工作台数据响应。
 *
 * @property batchId 任务单 ID
 * @property orderNo 任务单号
 * @property datasetId 数据集 ID
 * @property datasetName 数据集名称
 * @property annotationGuide 标注说明或标注文档
 * @property annotationSchema 标注结构 JSON 字符串
 * @property totalCount 任务总数
 * @property submittedCount 已提交数量
 * @property tasks 任务详情列表
 */
data class AnnotatorTaskWorkspaceResponse(
    val batchId: String,
    val orderNo: String,
    val datasetId: String,
    val datasetName: String,
    val annotationGuide: String?,
    val annotationSchema: String,
    val totalCount: Int,
    val submittedCount: Int,
    val tasks: List<AnnotatorTaskDetailResponse>,
)

/**
 * 单条标注结果提交输入。
 *
 * @property taskId 标注任务项 ID
 * @property itemId 数据项 ID
 * @property result 标注结果 JSON 字符串
 * @property isDisputed 是否存在争议
 * @property comment 标注备注
 */
data class AnnotationSubmissionInput(
    val taskId: String = "",
    val itemId: String = "",
    val result: String = "{}",
    val isDisputed: Boolean = false,
    val comment: String? = null,
)

/**
 * 批量提交标注结果请求数据。
 *
 * @property submissions 标注结果列表
 */
data class SubmitAnnotationBatchRequest(
    val submissions: List<AnnotationSubmissionInput> = emptyList(),
)

/**
 * 批量提交标注结果响应数据。
 *
 * @property message 提交结果消息
 * @property submittedCount 已提交数量
 * @property totalCount 任务总数
 */
data class SubmitAnnotationBatchResponse(
    val message: String,
    val submittedCount: Int,
    val totalCount: Int,
)


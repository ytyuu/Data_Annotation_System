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
)

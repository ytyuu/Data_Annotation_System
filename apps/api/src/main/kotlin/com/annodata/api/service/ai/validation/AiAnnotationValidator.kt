package com.annodata.api.service.ai.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

data class ClassificationSubOption(val value: String, val label: String)

data class ClassificationOption(
    val value: String,
    val label: String,
    val subSelectionMode: String?,
    val subOptions: List<ClassificationSubOption>,
)

data class ClassificationSchema(
    val selectionMode: String,
    val options: List<ClassificationOption>,
) {
    val optionsByValue: Map<String, ClassificationOption> = options.associateBy { it.value }
}

sealed class ClassificationSchemaParseResult {
    data class Valid(val schema: ClassificationSchema) : ClassificationSchemaParseResult()
    data class Invalid(val message: String) : ClassificationSchemaParseResult()
}

object AiAnnotationValidator {
    private val objectMapper = ObjectMapper()
    private val allowedResultFields = setOf("value", "values", "subValues")

    fun parseSchema(rawSchema: String): ClassificationSchemaParseResult {
        val root = runCatching { objectMapper.readTree(rawSchema) }.getOrNull()
            ?: return ClassificationSchemaParseResult.Invalid("标注结构不是合法 JSON")
        if (!root.isObject || root.path("type").asText() != "classification") {
            return ClassificationSchemaParseResult.Invalid("第一版 AI 标注仅支持 classification 类型")
        }

        val selectionMode = root.path("selectionMode").asText()
        if (selectionMode !in setOf("single", "multiple")) {
            return ClassificationSchemaParseResult.Invalid("标注结构 selectionMode 必须是 single 或 multiple")
        }

        val optionsNode = root.path("options")
        if (!optionsNode.isArray || optionsNode.size() < 2) {
            return ClassificationSchemaParseResult.Invalid("标注结构至少需要两个主选项")
        }

        val options = mutableListOf<ClassificationOption>()
        optionsNode.forEach { optionNode ->
            val value = optionNode.path("value").asText().trim()
            val label = optionNode.path("label").asText().trim()
            if (value.isEmpty() || label.isEmpty()) {
                return ClassificationSchemaParseResult.Invalid("主选项 value 和 label 不能为空")
            }

            val hasSubOptions = optionNode.path("hasSubOptions").asBoolean(false)
            val subSelectionMode = if (hasSubOptions) optionNode.path("subSelectionMode").asText() else null
            if (hasSubOptions && subSelectionMode !in setOf("single", "multiple")) {
                return ClassificationSchemaParseResult.Invalid("包含子选项时必须配置合法的 subSelectionMode")
            }

            val subOptions = if (hasSubOptions) {
                val subOptionsNode = optionNode.path("subOptions")
                if (!subOptionsNode.isArray || subOptionsNode.isEmpty) {
                    return ClassificationSchemaParseResult.Invalid("启用子选项后至少需要一个子选项")
                }
                subOptionsNode.map { subOptionNode ->
                    ClassificationSubOption(
                        value = subOptionNode.path("value").asText().trim(),
                        label = subOptionNode.path("label").asText().trim(),
                    )
                }
            } else {
                emptyList()
            }

            if (subOptions.any { it.value.isEmpty() || it.label.isEmpty() }) {
                return ClassificationSchemaParseResult.Invalid("子选项 value 和 label 不能为空")
            }
            if (subOptions.map { it.value }.distinct().size != subOptions.size) {
                return ClassificationSchemaParseResult.Invalid("同一主选项下的子选项 value 不能重复")
            }
            options += ClassificationOption(value, label, subSelectionMode, subOptions)
        }

        if (options.map { it.value }.distinct().size != options.size) {
            return ClassificationSchemaParseResult.Invalid("主选项 value 不能重复")
        }

        return ClassificationSchemaParseResult.Valid(ClassificationSchema(selectionMode, options))
    }

    fun validateResult(result: JsonNode, schema: ClassificationSchema): String? {
        if (!result.isObject) return "标注结果必须是 JSON 对象"
        val unknownFields = result.fieldNames().asSequence().filter { it !in allowedResultFields }.toList()
        if (unknownFields.isNotEmpty()) return "标注结果包含不支持的字段：${unknownFields.joinToString()}"

        val selectedValues = if (schema.selectionMode == "single") {
            if (result.has("values")) return "单选结果不能包含 values"
            val valueNode = result.get("value")
            if (valueNode?.isTextual != true || valueNode.asText().isBlank()) return "单选结果必须包含非空 value"
            listOf(valueNode.asText())
        } else {
            if (result.has("value")) return "多选结果不能包含 value"
            val valuesNode = result.get("values")
            if (valuesNode?.isArray != true || valuesNode.isEmpty) return "多选结果必须包含非空 values 数组"
            if (valuesNode.any { !it.isTextual || it.asText().isBlank() }) return "values 只能包含非空字符串"
            valuesNode.map { it.asText() }
        }

        if (selectedValues.distinct().size != selectedValues.size) return "标注结果不能包含重复主选项"
        if (selectedValues.any { it !in schema.optionsByValue }) return "标注结果包含未定义的主选项"

        val subValuesNode = result.get("subValues")
        if (subValuesNode != null && !subValuesNode.isObject) return "subValues 必须是 JSON 对象"
        val subValueKeys = subValuesNode?.fieldNames()?.asSequence()?.toSet().orEmpty()
        if (subValueKeys.any { it !in selectedValues }) return "subValues 只能包含已选中的主选项"

        selectedValues.forEach { selectedValue ->
            val option = schema.optionsByValue.getValue(selectedValue)
            val selectedSubValuesNode = subValuesNode?.get(selectedValue)
            if (option.subOptions.isEmpty()) {
                if (selectedSubValuesNode != null) return "无子选项的主选项不能出现在 subValues 中"
                return@forEach
            }

            if (selectedSubValuesNode?.isArray != true || selectedSubValuesNode.isEmpty) {
                return "选中「${option.label}」时必须选择子选项"
            }
            if (selectedSubValuesNode.any { !it.isTextual || it.asText().isBlank() }) {
                return "子选项只能包含非空字符串"
            }
            val selectedSubValues = selectedSubValuesNode.map { it.asText() }
            if (selectedSubValues.distinct().size != selectedSubValues.size) return "子选项不能重复"
            if (option.subSelectionMode == "single" && selectedSubValues.size != 1) return "单选子选项只能选择一项"
            val allowedSubValues = option.subOptions.map { it.value }.toSet()
            if (selectedSubValues.any { it !in allowedSubValues }) return "标注结果包含未定义的子选项"
        }

        return null
    }
}

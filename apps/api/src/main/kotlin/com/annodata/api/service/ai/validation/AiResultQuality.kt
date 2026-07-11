package com.annodata.api.service.ai.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object AiResultQuality {
    private val objectMapper = ObjectMapper()

    fun canonicalJson(node: JsonNode): String = canonicalize(node).toString()

    fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun selectedMainValues(result: JsonNode): Set<String> {
        val value = result.get("value")
        if (value?.isTextual == true) return setOf(value.asText())
        val values = result.get("values")
        return if (values?.isArray == true) values.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.toSet()
        else emptySet()
    }

    fun isSampled(batchId: UUID, itemId: UUID, samplingRatio: Double): Boolean {
        if (samplingRatio <= 0.0) return false
        if (samplingRatio >= 1.0) return true
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$batchId:$itemId".toByteArray(StandardCharsets.UTF_8))
        val bucket = BigInteger(1, digest.copyOfRange(0, 8)).mod(BigInteger.valueOf(1_000_000)).toInt()
        return bucket < (samplingRatio * 1_000_000).toInt()
    }

    private fun canonicalize(node: JsonNode): JsonNode {
        return when {
            node.isObject -> {
                val objectNode = objectMapper.createObjectNode()
                node.fieldNames().asSequence().sorted().forEach { name ->
                    objectNode.set<JsonNode>(name, canonicalize(node.get(name)))
                }
                objectNode
            }
            node.isArray -> {
                val arrayNode: ArrayNode = objectMapper.createArrayNode()
                node.forEach { arrayNode.add(canonicalize(it)) }
                arrayNode
            }
            else -> node.deepCopy<JsonNode>()
        }
    }
}

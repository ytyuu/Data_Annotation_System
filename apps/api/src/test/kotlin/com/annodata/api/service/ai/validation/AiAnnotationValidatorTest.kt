package com.annodata.api.service.ai.validation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AiAnnotationValidatorTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `parses and validates single classification with sub option`() {
        val parsed = AiAnnotationValidator.parseSchema(SINGLE_SCHEMA)
        val valid = assertInstanceOf(ClassificationSchemaParseResult.Valid::class.java, parsed)

        val error = AiAnnotationValidator.validateResult(
            objectMapper.readTree("""{"value":"risk","subValues":{"risk":["high"]}}"""),
            valid.schema,
        )

        assertNull(error)
    }

    @Test
    fun `rejects duplicate multiple selection and undefined sub option`() {
        val schema = (AiAnnotationValidator.parseSchema(MULTIPLE_SCHEMA) as ClassificationSchemaParseResult.Valid).schema

        assertEquals(
            "标注结果不能包含重复主选项",
            AiAnnotationValidator.validateResult(objectMapper.readTree("""{"values":["risk","risk"]}"""), schema),
        )
        assertEquals(
            "标注结果包含未定义的子选项",
            AiAnnotationValidator.validateResult(
                objectMapper.readTree("""{"values":["risk"],"subValues":{"risk":["unknown"]}}"""),
                schema,
            ),
        )
    }

    @Test
    fun `rejects unsupported schema and duplicate option values`() {
        assertInstanceOf(
            ClassificationSchemaParseResult.Invalid::class.java,
            AiAnnotationValidator.parseSchema("""{"type":"sequence","selectionMode":"single","options":[]}"""),
        )
        assertInstanceOf(
            ClassificationSchemaParseResult.Invalid::class.java,
            AiAnnotationValidator.parseSchema(
                """{"type":"classification","selectionMode":"single","options":[{"value":"same","label":"A"},{"value":"same","label":"B"}]}""",
            ),
        )
    }

    @Test
    fun `canonical json and sampling are deterministic`() {
        val first = objectMapper.readTree("""{"z":1,"a":{"y":2,"x":1}}""")
        val second = objectMapper.readTree("""{"a":{"x":1,"y":2},"z":1}""")
        assertEquals(AiResultQuality.canonicalJson(first), AiResultQuality.canonicalJson(second))
        assertEquals(
            AiResultQuality.sha256(AiResultQuality.canonicalJson(first)),
            AiResultQuality.sha256(AiResultQuality.canonicalJson(second)),
        )

        val batchId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val itemId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val sampled = AiResultQuality.isSampled(batchId, itemId, 0.25)
        assertEquals(sampled, AiResultQuality.isSampled(batchId, itemId, 0.25))
        assertFalse(AiResultQuality.isSampled(batchId, itemId, 0.0))
        assertTrue(AiResultQuality.isSampled(batchId, itemId, 1.0))
    }

    private companion object {
        const val SINGLE_SCHEMA = """
            {"type":"classification","selectionMode":"single","options":[
              {"value":"safe","label":"安全"},
              {"value":"risk","label":"风险","hasSubOptions":true,"subSelectionMode":"single","subOptions":[{"value":"high","label":"高风险"}]}
            ]}
        """
        const val MULTIPLE_SCHEMA = """
            {"type":"classification","selectionMode":"multiple","options":[
              {"value":"safe","label":"安全"},
              {"value":"risk","label":"风险","hasSubOptions":true,"subSelectionMode":"multiple","subOptions":[{"value":"high","label":"高风险"},{"value":"medium","label":"中风险"}]}
            ]}
        """
    }
}

package com.woorijip.api.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiSpendingAnalysisSchemaTests {
    @Test
    fun `uses only supported strict structured output keywords`() {
        val unsupported = keywordsIn(OpenAiSpendingAnalysisGenerator.responseSchema)
            .intersect(UNSUPPORTED_KEYWORDS)

        assertTrue(
            unsupported.isEmpty(),
            "strict 스키마가 지원하지 않는 키워드를 사용한다: $unsupported",
        )
    }

    @Test
    fun `requires every declared property`() {
        val schema = OpenAiSpendingAnalysisGenerator.responseSchema
        val properties = (schema["properties"] as Map<*, *>).keys.map(Any?::toString).toSet()
        val required = (schema["required"] as List<*>).map(Any?::toString).toSet()

        assertEquals(properties, required)
        assertEquals(false, schema["additionalProperties"])
    }

    private fun keywordsIn(value: Any?): Set<String> =
        when (value) {
            is Map<*, *> ->
                value.keys.mapNotNull { it as? String }.toSet() +
                    value.values.flatMap { keywordsIn(it) }
            is Iterable<*> -> value.flatMap { keywordsIn(it) }.toSet()
            else -> emptySet()
        }

    private companion object {
        val UNSUPPORTED_KEYWORDS = setOf(
            "uniqueItems",
            "minLength",
            "maxLength",
            "pattern",
            "format",
            "minimum",
            "maximum",
            "multipleOf",
            "minItems",
            "maxItems",
            "patternProperties",
            "unevaluatedProperties",
            "propertyNames",
            "minProperties",
            "maxProperties",
            "contains",
            "minContains",
            "maxContains",
            "if",
            "then",
            "else",
            "not",
            "dependentRequired",
            "dependentSchemas",
            "oneOf",
            "allOf",
        )
    }
}

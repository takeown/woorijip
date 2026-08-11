package com.woorijip.api.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiSpendingQuestionSchemaTests {
    @Test
    fun `uses only keywords that strict structured outputs allows`() {
        val used = keywordsIn(OpenAiSpendingQuestionInterpreter.responseSchema)
        assertTrue(used.intersect(UNSUPPORTED_KEYWORDS).isEmpty())
    }

    @Test
    fun `requires every declared property and limits supported intents`() {
        val schema = OpenAiSpendingQuestionInterpreter.responseSchema
        val properties = schema["properties"] as Map<*, *>
        val required = (schema["required"] as List<*>).map(Any?::toString).toSet()
        val intent = properties["intent"] as Map<*, *>

        assertEquals(properties.keys.map(Any?::toString).toSet(), required)
        assertEquals(false, schema["additionalProperties"])
        assertEquals(
            setOf("TOTAL", "CATEGORY", "LARGEST_TRANSACTION", null),
            (intent["enum"] as List<*>).toSet(),
        )
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

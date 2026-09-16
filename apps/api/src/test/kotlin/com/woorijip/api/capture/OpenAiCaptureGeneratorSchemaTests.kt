package com.woorijip.api.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiCaptureGeneratorSchemaTests {
    @Test
    fun usesOnlyStrictStructuredOutputKeywords() {
        val unsupported = keywordsIn(OpenAiCaptureGenerator.schema).intersect(UNSUPPORTED_KEYWORDS)
        assertTrue(unsupported.isEmpty(), "unsupported strict schema keywords: " + unsupported)
    }

    @Test
    fun requiresEveryDeclaredProperty() {
        val root = OpenAiCaptureGenerator.schema
        assertEquals((root["properties"] as Map<*, *>).keys, (root["required"] as List<*>).toSet())

        val entries = (root["properties"] as Map<*, *>)["entries"] as Map<*, *>
        val item = entries["items"] as Map<*, *>
        assertEquals((item["properties"] as Map<*, *>).keys, (item["required"] as List<*>).toSet())
        assertEquals(false, root["additionalProperties"])
        assertEquals(false, item["additionalProperties"])
    }

    @Test
    fun labelsPngAndJpegDataCorrectly() {
        assertEquals("image/png", captureMediaType(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        assertEquals("image/jpeg", captureMediaType(byteArrayOf(-1, -40, -1)))
    }

    private fun keywordsIn(value: Any?): Set<String> = when (value) {
        is Map<*, *> -> value.keys.mapNotNull { it as? String }.toSet() + value.values.flatMap(::keywordsIn)
        is Iterable<*> -> value.flatMap(::keywordsIn).toSet()
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
            "oneOf",
            "allOf",
        )
    }
}

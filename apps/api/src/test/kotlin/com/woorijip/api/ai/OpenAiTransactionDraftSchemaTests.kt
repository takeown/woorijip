package com.woorijip.api.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OpenAI strict Structured Outputs는 허용하는 JSON Schema 키워드가 제한적이다.
 * 지원하지 않는 키워드가 하나라도 있으면 요청 전체가 400으로 거부되는데,
 * 다른 AI 테스트는 가짜 generator를 사용하므로 이 스키마를 검증하지 않는다.
 */
class OpenAiTransactionDraftSchemaTests {
    @Test
    fun `uses only keywords that strict structured outputs allows`() {
        val used = keywordsIn(OpenAiTransactionDraftGenerator.responseSchema)
        val unsupported = used.intersect(UNSUPPORTED_KEYWORDS)

        assertTrue(
            unsupported.isEmpty(),
            "strict 스키마가 지원하지 않는 키워드를 사용한다: $unsupported",
        )
    }

    @Test
    fun `requires every declared property so strict mode accepts the schema`() {
        val schema = OpenAiTransactionDraftGenerator.responseSchema
        val properties = (schema["properties"] as Map<*, *>).keys.map(Any?::toString).toSet()
        val required = (schema["required"] as List<*>).map(Any?::toString).toSet()

        assertEquals(properties, required)
        assertEquals(false, schema["additionalProperties"])
    }

    @Test
    fun `allows QR and stored value account types`() {
        val properties = OpenAiTransactionDraftGenerator.responseSchema["properties"] as Map<*, *>
        val paymentMethod = properties["paymentMethod"] as Map<*, *>
        val storedValueAccountType = properties["storedValueAccountType"] as Map<*, *>

        assertTrue((paymentMethod["enum"] as List<*>).contains("QR"))
        assertEquals(
            setOf("ONNURI_GIFT_CERTIFICATE", "PREGNANCY_VOUCHER", null),
            (storedValueAccountType["enum"] as List<*>).toSet(),
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
        /**
         * 2026-07-28 기준으로 확인한 미지원 키워드. `uniqueItems`를 쓴 탓에 AI 초안이
         * 502로 실패한 적이 있어 회귀를 막는다.
         */
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

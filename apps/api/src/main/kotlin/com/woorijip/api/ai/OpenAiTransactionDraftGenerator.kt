package com.woorijip.api.ai

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

@Component
class OpenAiTransactionDraftGenerator(
    private val properties: OpenAiProperties,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : TransactionDraftGenerator {
    private val restClient = restClientBuilder.baseUrl("https://api.openai.com").build()

    override fun generate(
        message: String,
        context: TransactionDraftGenerationContext,
    ): GeneratedTransactionDraft {
        if (properties.apiKey.isBlank()) {
            throw DraftGenerationException("OpenAI API 키가 설정되지 않았습니다.")
        }

        val response = try {
            restClient
                .post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .body(requestBody(message, context))
                .retrieve()
                .body(OpenAiResponse::class.java)
        } catch (exception: RestClientException) {
            throw DraftGenerationException("OpenAI 응답을 받지 못했습니다.", exception)
        } ?: throw DraftGenerationException("OpenAI 응답이 비어 있습니다.")

        val outputText = response.output
            .asSequence()
            .filter { item -> item.type == "message" }
            .flatMap { item -> item.content.asSequence() }
            .firstOrNull { content -> content.type == "output_text" }
            ?.text
            ?: throw DraftGenerationException("OpenAI 응답에 거래 초안이 없습니다.")

        return try {
            objectMapper.readValue(outputText, GeneratedTransactionDraft::class.java)
        } catch (exception: Exception) {
            throw DraftGenerationException("OpenAI 거래 초안을 해석하지 못했습니다.", exception)
        }
    }

    private fun requestBody(
        message: String,
        context: TransactionDraftGenerationContext,
    ): Map<String, Any> =
        mapOf(
            "model" to properties.model,
            "instructions" to instructions(context),
            "input" to message,
            "store" to false,
            "max_output_tokens" to 400,
            "safety_identifier" to "household-user-${context.currentUserId}",
            "reasoning" to mapOf("effort" to "low"),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "transaction_draft",
                    "strict" to true,
                    "schema" to responseSchema,
                ),
            ),
        )

    private fun instructions(context: TransactionDraftGenerationContext): String =
        """
        사용자의 한국어 입력에서 가계부 거래 한 건만 구조화하세요.
        현재 시각은 ${context.currentTime}입니다.
        날짜나 시각이 없으면 현재 시각을 사용하세요.
        결제자 언급이 없으면 ME, 배우자나 아내나 남편이 결제했다면 PARTNER를 사용하세요.
        카테고리는 짧은 한국어 명사로 추론하세요.
        가맹점, 양의 정수 KRW 금액, 카테고리, ISO 8601 발생 시각, 결제자를 모두 알면 READY입니다.
        거래 입력이지만 필수값이 부족하면 NEEDS_CLARIFICATION과 한 가지 짧은 질문을 반환하세요.
        소비 분석, 조회, 일반 대화 등 거래 한 건 입력이 아니면 UNSUPPORTED를 반환하세요.
        READY가 아니면 알 수 없는 거래 필드는 null로 반환하세요.
        """.trimIndent()

    private data class OpenAiResponse(
        val output: List<OpenAiOutputItem> = emptyList(),
    )

    private data class OpenAiOutputItem(
        val type: String = "",
        val content: List<OpenAiContent> = emptyList(),
    )

    private data class OpenAiContent(
        val type: String = "",
        val text: String? = null,
    )

    private companion object {
        val nullableString = mapOf("type" to listOf("string", "null"))
        val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "status" to mapOf(
                    "type" to "string",
                    "enum" to GeneratedDraftStatus.entries.map(GeneratedDraftStatus::name),
                ),
                "merchant" to nullableString,
                "amount" to mapOf("type" to listOf("integer", "null")),
                "category" to nullableString,
                "occurredAt" to nullableString,
                "payer" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to listOf("ME", "PARTNER", null),
                ),
                "message" to nullableString,
            ),
            "required" to listOf(
                "status",
                "merchant",
                "amount",
                "category",
                "occurredAt",
                "payer",
                "message",
            ),
        )
    }
}

class DraftGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

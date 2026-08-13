package com.woorijip.api.ai

import com.woorijip.api.statistics.GeneratedSpendingAnalysis
import com.woorijip.api.statistics.GeneratedSpendingAnalysisStatus
import com.woorijip.api.statistics.SpendingAnalysisGenerationContext
import com.woorijip.api.statistics.SpendingAnalysisGenerationException
import com.woorijip.api.statistics.SpendingAnalysisGenerator
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

@Component
class OpenAiSpendingAnalysisGenerator(
    private val properties: OpenAiProperties,
    private val safetyIdentifier: OpenAiSafetyIdentifier,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : SpendingAnalysisGenerator {
    private val restClient = restClientBuilder.baseUrl("https://api.openai.com").build()

    override fun generate(
        question: String,
        context: SpendingAnalysisGenerationContext,
    ): GeneratedSpendingAnalysis {
        if (properties.apiKey.isBlank()) {
            throw SpendingAnalysisGenerationException("OpenAI API 키가 설정되지 않았습니다.")
        }

        val response = try {
            restClient
                .post()
                .uri("/v1/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .body(requestBody(question, context))
                .retrieve()
                .body(OpenAiResponse::class.java)
        } catch (exception: RestClientResponseException) {
            throw SpendingAnalysisGenerationException(
                "OpenAI가 ${exception.statusCode.value()} 응답을 반환했습니다.",
            )
        } catch (exception: RestClientException) {
            throw SpendingAnalysisGenerationException(
                "OpenAI 응답을 받지 못했습니다. ${exception.javaClass.simpleName}",
            )
        } ?: throw SpendingAnalysisGenerationException("OpenAI 응답이 비어 있습니다.")

        val outputText = response.output
            .asSequence()
            .filter { item -> item.type == "message" }
            .flatMap { item -> item.content.asSequence() }
            .firstOrNull { content -> content.type == "output_text" }
            ?.text
            ?: throw SpendingAnalysisGenerationException(
                "OpenAI 응답에 가계 분석이 없습니다. status=${response.status ?: "unknown"}",
            )

        return try {
            objectMapper.readValue(outputText, GeneratedSpendingAnalysis::class.java)
        } catch (exception: Exception) {
            throw SpendingAnalysisGenerationException("OpenAI 가계 분석을 해석하지 못했습니다.", exception)
        }
    }

    private fun requestBody(
        question: String,
        context: SpendingAnalysisGenerationContext,
    ): Map<String, Any> =
        mapOf(
            "model" to properties.model,
            "instructions" to instructions(context),
            "input" to question,
            "store" to false,
            "max_output_tokens" to properties.analysisMaxOutputTokens.coerceIn(100, 1_000),
            "safety_identifier" to safetyIdentifier.forUser(context.currentUserId),
            "reasoning" to mapOf("effort" to "low"),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "spending_analysis",
                    "strict" to true,
                    "schema" to responseSchema,
                ),
            ),
        )

    private fun instructions(context: SpendingAnalysisGenerationContext): String =
        """
        저장된 가계 거래내역에 관한 한국어 질문에만 답하세요.
        현재 시각은 ${context.currentTime}입니다.
        아래 JSON 거래만 사실과 계산의 근거로 사용하고, 추측하거나 없는 거래를 만들지 마세요.
        payer의 ME는 질문한 사용자, PARTNER는 배우자입니다.
        ANSWERED이면 짧고 이해하기 쉬운 한국어 답변과 실제 근거 reference 1~5개를 반환하세요.
        질문에 답하는 데 필요한 거래를 우선 근거로 고르세요.
        거래내역과 무관한 요청이면 UNSUPPORTED를 반환하고 answer는 null, evidenceReferences는 빈 배열로 두세요.
        제공 데이터가 최근 일부로 제한됐으면 답변에서 그 한계를 분명히 밝히세요.

        dataLimited: ${context.dataLimited}
        transactions: ${objectMapper.writeValueAsString(context.records)}
        """.trimIndent()

    private data class OpenAiResponse(
        val status: String? = null,
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

    internal companion object {
        val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "status" to mapOf(
                    "type" to "string",
                    "enum" to GeneratedSpendingAnalysisStatus.entries.map(GeneratedSpendingAnalysisStatus::name),
                ),
                "answer" to mapOf("type" to listOf("string", "null")),
                "evidenceReferences" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                ),
            ),
            "required" to listOf("status", "answer", "evidenceReferences"),
        )
    }
}

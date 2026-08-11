package com.woorijip.api.ai

import com.woorijip.api.statistics.SpendingPayer
import com.woorijip.api.statistics.SpendingPeriod
import com.woorijip.api.transaction.TransactionCategory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

@Component
class OpenAiSpendingQuestionInterpreter(
    private val properties: OpenAiProperties,
    private val safetyIdentifier: OpenAiSafetyIdentifier,
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
) : SpendingQuestionInterpreter {
    private val restClient = restClientBuilder.baseUrl("https://api.openai.com").build()

    override fun interpret(
        question: String,
        context: SpendingQuestionInterpretationContext,
    ): GeneratedSpendingQuestion {
        if (properties.apiKey.isBlank()) {
            throw SpendingQuestionInterpretationException("OpenAI API 키가 설정되지 않았습니다.")
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
            throw SpendingQuestionInterpretationException(
                "OpenAI가 ${exception.statusCode.value()} 응답을 반환했습니다.",
            )
        } catch (exception: RestClientException) {
            throw SpendingQuestionInterpretationException(
                "OpenAI 응답을 받지 못했습니다. ${exception.javaClass.simpleName}",
            )
        } ?: throw SpendingQuestionInterpretationException("OpenAI 응답이 비어 있습니다.")

        val outputText = response.output
            .asSequence()
            .filter { item -> item.type == "message" }
            .flatMap { item -> item.content.asSequence() }
            .firstOrNull { content -> content.type == "output_text" }
            ?.text
            ?: throw SpendingQuestionInterpretationException(
                "OpenAI 응답에 지출 질문 해석이 없습니다. status=${response.status ?: "unknown"}",
            )

        return try {
            objectMapper.readValue(outputText, GeneratedSpendingQuestion::class.java)
        } catch (exception: Exception) {
            throw SpendingQuestionInterpretationException("OpenAI 지출 질문을 해석하지 못했습니다.", exception)
        }
    }

    private fun requestBody(
        question: String,
        context: SpendingQuestionInterpretationContext,
    ): Map<String, Any> =
        mapOf(
            "model" to properties.model,
            "instructions" to instructions(context),
            "input" to question,
            "store" to false,
            "max_output_tokens" to 250,
            "safety_identifier" to safetyIdentifier.forUser(context.currentUserId),
            "reasoning" to mapOf("effort" to "low"),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "spending_question",
                    "strict" to true,
                    "schema" to responseSchema,
                ),
            ),
        )

    private fun instructions(context: SpendingQuestionInterpretationContext): String =
        """
        사용자의 한국어 가계 지출 질문을 서버가 실행할 수 있는 제한된 질의 하나로 구조화하세요.
        오늘 서울 날짜는 ${context.currentDate}입니다.
        지원 범위는 기간의 총지출, 특정 표준 카테고리 지출, 가장 큰 단일 거래입니다.
        TOTAL은 기간 총지출, CATEGORY는 특정 카테고리 합계와 이전 같은 기간 비교,
        LARGEST_TRANSACTION은 기간 안에서 금액이 가장 큰 단일 거래를 뜻합니다.
        기간은 DAY, WEEK, MONTH 중 하나입니다. 이번 달·지난달·오늘·어제·이번 주·지난 주 같은
        상대 표현을 오늘 날짜 기준의 ISO 8601 referenceDate로 변환하세요.
        결제자 언급이 없으면 ALL, 내가 쓴 돈은 ME, 배우자·아내·남편이 쓴 돈은 PARTNER입니다.
        카테고리는 제공된 영문 코드 중 하나만 사용하세요.
        FOOD는 식비, HOUSING은 주거, TRANSPORT는 교통, LIVING은 생활, CHILDCARE는 육아,
        HEALTH는 건강, LEISURE는 여가, EDUCATION은 교육, FINANCE_INSURANCE는 금융·보험,
        FAMILY_EVENT는 경조사, OTHER는 기타입니다.
        CATEGORY일 때만 category를 채우고, TOTAL과 LARGEST_TRANSACTION에서는 null로 반환하세요.
        예산, 미래 예측, 원인 추론, 거래 입력, 가맹점 검색, 태그 분석, 일반 대화처럼 지원 범위
        밖의 질문은 UNSUPPORTED로 반환하고 나머지 필드는 null로 반환하세요.
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
                    "enum" to GeneratedSpendingQuestionStatus.entries.map(GeneratedSpendingQuestionStatus::name),
                ),
                "intent" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to SpendingQuestionIntent.entries.map(SpendingQuestionIntent::name) + null,
                ),
                "period" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to SpendingPeriod.entries.map(SpendingPeriod::name) + null,
                ),
                "referenceDate" to mapOf("type" to listOf("string", "null")),
                "payer" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to SpendingPayer.entries.map(SpendingPayer::name) + null,
                ),
                "category" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to TransactionCategory.entries.map(TransactionCategory::name) + null,
                ),
            ),
            "required" to listOf("status", "intent", "period", "referenceDate", "payer", "category"),
        )
    }
}

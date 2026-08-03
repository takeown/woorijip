package com.woorijip.api.ai

import com.woorijip.api.storedvalue.StoredValueAccountType
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionTag
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

@Component
class OpenAiTransactionDraftGenerator(
    private val properties: OpenAiProperties,
    private val safetyIdentifier: OpenAiSafetyIdentifier,
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
        } catch (exception: RestClientResponseException) {
            throw DraftGenerationException(
                "OpenAI가 ${exception.statusCode.value()} 응답을 반환했습니다.",
            )
        } catch (exception: RestClientException) {
            throw DraftGenerationException("OpenAI 응답을 받지 못했습니다. ${exception.javaClass.simpleName}")
        } ?: throw DraftGenerationException("OpenAI 응답이 비어 있습니다.")

        val outputText = response.output
            .asSequence()
            .filter { item -> item.type == "message" }
            .flatMap { item -> item.content.asSequence() }
            .firstOrNull { content -> content.type == "output_text" }
            ?.text
            ?: throw DraftGenerationException(
                "OpenAI 응답에 거래 초안이 없습니다. status=${response.status ?: "unknown"}",
            )

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
            "safety_identifier" to safetyIdentifier.forUser(context.currentUserId),
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
        결제 경로는 CARD, CASH, QR 중 하나를 사용하세요. 카드 결제면 국내 카드사를 cardIssuer로 반환하세요.
        카드사가 언급되지 않은 카드 결제는 NEEDS_CLARIFICATION으로 카드사를 질문하세요.
        현금이나 QR 결제면 cardIssuer는 null입니다.
        온누리상품권을 사용하면 storedValueAccountType은 ONNURI_GIFT_CERTIFICATE입니다.
        임산부 바우처를 사용하면 storedValueAccountType은 PREGNANCY_VOUCHER입니다.
        별도 잔액을 사용하지 않으면 storedValueAccountType은 null입니다.
        QR 결제는 반드시 별도 잔액을 사용하며, 사용자가 QR이라고 말했다면 카드나 현금인지 다시 질문하지 마세요.
        상품권이나 바우처 사용만 언급하고 QR인지 연결 카드인지 알 수 없으면 결제 경로를 질문하세요.
        카테고리는 제공된 영문 코드 중 하나만 사용하세요.
        FOOD는 식비, HOUSING은 주거, TRANSPORT는 교통, LIVING은 생활, HEALTH는 건강,
        LEISURE는 여가, EDUCATION은 교육, FINANCE_INSURANCE는 금융·보험,
        FAMILY_EVENT는 경조사, CHILDCARE는 육아, OTHER는 기타입니다.
        태그는 SUBSCRIPTION, UTILITY, RECURRING_PAYMENT 중 해당하는 값을 모두 사용하세요.
        내역은 구매 품목이나 사용 목적을 사용자가 말한 경우에만 짧게 정리하고, 알 수 없으면 null로 반환하세요.
        내역이 없다는 이유로 추가 질문하지 마세요.
        가맹점, 양의 정수 KRW 금액, 카테고리, ISO 8601 발생 시각, 결제자, 결제수단을 모두 알면 READY입니다.
        거래 입력이지만 필수값이 부족하면 NEEDS_CLARIFICATION과 한 가지 짧은 질문을 반환하세요.
        소비 분석, 조회, 일반 대화 등 거래 한 건 입력이 아니면 UNSUPPORTED를 반환하세요.
        READY가 아니면 알 수 없는 거래 필드는 null로 반환하세요.
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
                "description" to nullableString,
                "amount" to mapOf("type" to listOf("integer", "null")),
                "category" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to TransactionCategory.entries.map(TransactionCategory::name) + null,
                ),
                // strict 스키마는 지원 키워드가 제한적이라 uniqueItems를 쓰지 않는다.
                // 중복 태그는 Set<TransactionTag>로 역직렬화하면서 제거된다.
                "tags" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "string",
                        "enum" to TransactionTag.entries.map(TransactionTag::name),
                    ),
                ),
                "occurredAt" to nullableString,
                "payer" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to listOf("ME", "PARTNER", null),
                ),
                "paymentMethod" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to listOf(
                        PaymentMethod.CARD.name,
                        PaymentMethod.CASH.name,
                        PaymentMethod.QR.name,
                        null,
                    ),
                ),
                "cardIssuer" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to CardIssuer.entries.map(CardIssuer::name) + null,
                ),
                "storedValueAccountType" to mapOf(
                    "type" to listOf("string", "null"),
                    "enum" to StoredValueAccountType.entries.map(StoredValueAccountType::name) + null,
                ),
                "message" to nullableString,
            ),
            "required" to listOf(
                "status",
                "merchant",
                "description",
                "amount",
                "category",
                "tags",
                "occurredAt",
                "payer",
                "paymentMethod",
                "cardIssuer",
                "storedValueAccountType",
                "message",
            ),
        )
    }
}

class DraftGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

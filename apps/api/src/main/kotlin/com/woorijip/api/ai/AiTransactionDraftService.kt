package com.woorijip.api.ai

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.household.HouseholdMember
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneId

data class AiTransactionDraft(
    val status: GeneratedDraftStatus,
    val merchant: String? = null,
    val description: String? = null,
    val amount: Long? = null,
    val category: String? = null,
    val occurredAt: OffsetDateTime? = null,
    val payerId: Long? = null,
    val payerDisplayName: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val cardIssuer: CardIssuer? = null,
    val message: String,
)

@Service
class AiTransactionDraftService(
    private val transactionDraftGenerator: TransactionDraftGenerator,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    fun create(
        currentUser: CurrentUser,
        messages: List<String>,
    ): AiTransactionDraft {
        val members = householdMembershipRepository.findMembersByHouseholdId(currentUser.householdId)
        val generated = try {
            transactionDraftGenerator.generate(
                conversationPrompt(messages),
                TransactionDraftGenerationContext(
                    currentUserId = currentUser.id,
                    currentTime = OffsetDateTime.now(SEOUL),
                ),
            )
        } catch (_: DraftGenerationException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 거래 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }

        return when (generated.status) {
            GeneratedDraftStatus.READY -> readyDraft(currentUser, members, generated)
            GeneratedDraftStatus.NEEDS_CLARIFICATION -> AiTransactionDraft(
                status = generated.status,
                message = generated.message?.takeIf(String::isNotBlank)
                    ?: "거래를 기록하려면 정보가 조금 더 필요합니다.",
            )
            GeneratedDraftStatus.UNSUPPORTED -> AiTransactionDraft(
                status = generated.status,
                message = "현재는 거래 한 건 입력만 도와드릴 수 있습니다.",
            )
        }
    }

    private fun readyDraft(
        currentUser: CurrentUser,
        members: List<HouseholdMember>,
        generated: GeneratedTransactionDraft,
    ): AiTransactionDraft {
        val merchant = generated.merchant?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }
        val description = generated.description?.trim()?.takeIf { it.isNotEmpty() && it.length <= 500 }
        val amount = generated.amount?.takeIf { it > 0 }
        val category = generated.category?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 }
        val occurredAt = generated.occurredAt?.let { value ->
            runCatching { OffsetDateTime.parse(value) }.getOrNull()
        }
        val payer = resolvePayer(currentUser, members, generated.payer)
        val hasValidPaymentDetails = when (generated.paymentMethod) {
            PaymentMethod.CARD -> generated.cardIssuer != null
            PaymentMethod.CASH -> generated.cardIssuer == null
            PaymentMethod.UNKNOWN, null -> false
        }

        if (
            merchant == null ||
            amount == null ||
            category == null ||
            occurredAt == null ||
            payer == null ||
            !hasValidPaymentDetails
        ) {
            return AiTransactionDraft(
                status = GeneratedDraftStatus.NEEDS_CLARIFICATION,
                message = "거래 정보를 정확히 확인할 수 없습니다. 가맹점, 금액, 결제자, 결제수단을 포함해 다시 입력해 주세요.",
            )
        }

        return AiTransactionDraft(
            status = GeneratedDraftStatus.READY,
            merchant = merchant,
            description = description,
            amount = amount,
            category = category,
            occurredAt = occurredAt,
            payerId = payer.userId,
            payerDisplayName = payer.displayName,
            paymentMethod = generated.paymentMethod,
            cardIssuer = generated.cardIssuer,
            message = "아래 거래 내용을 확인해 주세요.",
        )
    }

    private fun resolvePayer(
        currentUser: CurrentUser,
        members: List<HouseholdMember>,
        generatedPayer: GeneratedPayer?,
    ): HouseholdMember? =
        when (generatedPayer) {
            GeneratedPayer.ME -> members.singleOrNull { member -> member.userId == currentUser.id }
            GeneratedPayer.PARTNER -> members.singleOrNull { member -> member.userId != currentUser.id }
            null -> null
        }

    private fun conversationPrompt(messages: List<String>): String =
        messages
            .mapIndexed { index, message ->
                if (index == 0) {
                    "최초 거래 입력:\n$message"
                } else {
                    "추가 답변 $index:\n$message"
                }
            }.joinToString("\n\n")

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

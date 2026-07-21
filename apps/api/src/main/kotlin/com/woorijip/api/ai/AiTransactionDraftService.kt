package com.woorijip.api.ai

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.household.HouseholdMember
import com.woorijip.api.household.HouseholdMembershipRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneId

data class AiTransactionDraft(
    val status: GeneratedDraftStatus,
    val merchant: String? = null,
    val amount: Long? = null,
    val category: String? = null,
    val occurredAt: OffsetDateTime? = null,
    val payerId: Long? = null,
    val payerDisplayName: String? = null,
    val message: String,
)

@Service
class AiTransactionDraftService(
    private val transactionDraftGenerator: TransactionDraftGenerator,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    fun create(
        currentUser: CurrentUser,
        message: String,
    ): AiTransactionDraft {
        val members = householdMembershipRepository.findMembersByHouseholdId(currentUser.householdId)
        val generated = try {
            transactionDraftGenerator.generate(
                message,
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
        val amount = generated.amount?.takeIf { it > 0 }
        val category = generated.category?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 }
        val occurredAt = generated.occurredAt?.let { value ->
            runCatching { OffsetDateTime.parse(value) }.getOrNull()
        }
        val payer = resolvePayer(currentUser, members, generated.payer)

        if (merchant == null || amount == null || category == null || occurredAt == null || payer == null) {
            return AiTransactionDraft(
                status = GeneratedDraftStatus.NEEDS_CLARIFICATION,
                message = "거래 정보를 정확히 확인할 수 없습니다. 가맹점, 금액, 결제자를 포함해 다시 입력해 주세요.",
            )
        }

        return AiTransactionDraft(
            status = GeneratedDraftStatus.READY,
            merchant = merchant,
            amount = amount,
            category = category,
            occurredAt = occurredAt,
            payerId = payer.userId,
            payerDisplayName = payer.displayName,
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

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.household.HouseholdMembershipRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

enum class PayerFilter {
    ALL,
    ME,
    PARTNER,
}

data class TransactionDraft(
    val payerId: Long,
    val merchant: String,
    val amount: Long,
    val category: String,
    val paymentMethod: PaymentMethod,
    val cardIssuer: CardIssuer?,
    val occurredAt: OffsetDateTime,
)

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Transactional
    fun create(
        currentUser: CurrentUser,
        draft: TransactionDraft,
    ): Transaction {
        requireHouseholdMember(currentUser.householdId, draft.payerId)
        requirePaymentDetails(draft.paymentMethod, draft.cardIssuer)

        return transactionRepository.save(
            Transaction(
                householdId = currentUser.householdId,
                payerId = draft.payerId,
                merchant = draft.merchant,
                amount = draft.amount,
                category = draft.category,
                paymentMethod = draft.paymentMethod,
                cardIssuer = draft.cardIssuer,
                occurredAt = draft.occurredAt,
                createdAt = OffsetDateTime.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun findAll(
        currentUser: CurrentUser,
        payerFilter: PayerFilter,
    ): List<Transaction> =
        when (payerFilter) {
            PayerFilter.ALL ->
                transactionRepository.findAllByHouseholdIdOrderByOccurredAtDescIdDesc(currentUser.householdId)
            PayerFilter.ME ->
                transactionRepository.findAllByHouseholdIdAndPayerIdOrderByOccurredAtDescIdDesc(
                    currentUser.householdId,
                    currentUser.id,
                )
            PayerFilter.PARTNER ->
                transactionRepository.findAllByHouseholdIdAndPayerIdNotOrderByOccurredAtDescIdDesc(
                    currentUser.householdId,
                    currentUser.id,
                )
        }

    private fun requireHouseholdMember(
        householdId: Long,
        payerId: Long,
    ) {
        val isMember = householdMembershipRepository.existsByHouseholdIdAndUserId(householdId, payerId)
        if (!isMember) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "결제자는 현재 가구의 구성원이어야 합니다.")
        }
    }

    private fun requirePaymentDetails(
        paymentMethod: PaymentMethod,
        cardIssuer: CardIssuer?,
    ) {
        val isValid = when (paymentMethod) {
            PaymentMethod.CARD -> cardIssuer != null
            PaymentMethod.CASH -> cardIssuer == null
            PaymentMethod.UNKNOWN -> false
        }
        if (!isValid) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "결제수단과 카드사를 확인해 주세요.")
        }
    }
}

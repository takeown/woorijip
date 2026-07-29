package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import com.woorijip.api.household.HouseholdMembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

enum class PayerFilter {
    ALL,
    ME,
    PARTNER,
}

data class TransactionDraft(
    val payerId: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val category: TransactionCategory,
    val tags: Set<TransactionTag> = emptySet(),
    val classificationSource: ClassificationSource = ClassificationSource.USER,
    val paymentMethod: PaymentMethod,
    val cardIssuer: CardIssuer?,
    val occurredAt: OffsetDateTime,
)

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionTagRepository: TransactionTagRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
    private val merchantClassificationRuleService: MerchantClassificationRuleService,
) {
    @Transactional
    fun create(
        currentUser: CurrentUser,
        draft: TransactionDraft,
        classificationRuleId: Long? = null,
        saveMerchantRule: Boolean = false,
    ): Transaction {
        requireHouseholdMember(currentUser.householdId, draft.payerId)
        requirePaymentDetails(draft.paymentMethod, draft.cardIssuer)
        val now = OffsetDateTime.now()
        val classificationSource = resolveClassificationSource(
            currentUser,
            draft,
            classificationRuleId,
        )

        val saved = transactionRepository.save(
            Transaction(
                householdId = currentUser.householdId,
                payerId = draft.payerId,
                merchant = draft.merchant,
                description = draft.description,
                amount = draft.amount,
                category = draft.category,
                classificationSource = classificationSource,
                classificationConfidence = confidenceFor(classificationSource),
                classificationConfirmedAt = now,
                paymentMethod = draft.paymentMethod,
                cardIssuer = draft.cardIssuer,
                occurredAt = draft.occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val transactionId = requireNotNull(saved.id)
        transactionTagRepository.replaceAll(transactionId, draft.tags)
        if (saveMerchantRule) {
            merchantClassificationRuleService.save(
                currentUser = currentUser,
                merchant = draft.merchant,
                category = draft.category,
                tags = draft.tags,
                now = now,
            )
        }
        return saved.copy(tags = draft.tags)
    }

    @Transactional(readOnly = true)
    fun findAll(
        currentUser: CurrentUser,
        payerFilter: PayerFilter,
    ): List<Transaction> {
        val transactions = when (payerFilter) {
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
        return withTags(transactions)
    }

    @Transactional
    fun update(
        currentUser: CurrentUser,
        transactionId: Long,
        expectedUpdatedAt: OffsetDateTime,
        draft: TransactionDraft,
    ): Transaction {
        requireHouseholdMember(currentUser.householdId, draft.payerId)
        requirePaymentDetails(draft.paymentMethod, draft.cardIssuer)
        val updatedAt = OffsetDateTime.now()
        val updated = transactionRepository.updateIfUnchanged(
            id = transactionId,
            householdId = currentUser.householdId,
            expectedUpdatedAt = expectedUpdatedAt,
            payerId = draft.payerId,
            merchant = draft.merchant,
            description = draft.description,
            amount = draft.amount,
            category = draft.category.name,
            paymentMethod = draft.paymentMethod.name,
            cardIssuer = draft.cardIssuer?.name,
            occurredAt = draft.occurredAt,
            updatedAt = updatedAt,
        )
        if (updated != 1) {
            throwNotFoundOrConflict(currentUser, transactionId)
        }
        transactionTagRepository.replaceAll(transactionId, draft.tags)
        val transaction = requireNotNull(
            transactionRepository.findByIdAndHouseholdId(transactionId, currentUser.householdId),
        )
        return transaction.copy(tags = draft.tags)
    }

    @Transactional
    fun delete(
        currentUser: CurrentUser,
        transactionId: Long,
        expectedUpdatedAt: OffsetDateTime,
    ) {
        val deleted = transactionRepository.deleteIfUnchanged(
            id = transactionId,
            householdId = currentUser.householdId,
            expectedUpdatedAt = expectedUpdatedAt,
        )
        if (deleted != 1) {
            throwNotFoundOrConflict(currentUser, transactionId)
        }
    }

    private fun withTags(transactions: List<Transaction>): List<Transaction> {
        val tagsByTransactionId = transactionTagRepository.findAllByTransactionIds(
            transactions.mapNotNull(Transaction::id),
        )
        return transactions.map { transaction ->
            transaction.copy(tags = tagsByTransactionId[transaction.id].orEmpty())
        }
    }

    private fun confidenceFor(source: ClassificationSource): ClassificationConfidence =
        when (source) {
            ClassificationSource.USER -> ClassificationConfidence.HIGH
            ClassificationSource.MERCHANT_RULE -> ClassificationConfidence.HIGH
            ClassificationSource.HISTORY -> ClassificationConfidence.MEDIUM
            ClassificationSource.AI -> ClassificationConfidence.LOW
            ClassificationSource.MIGRATION -> ClassificationConfidence.LOW
        }

    private fun resolveClassificationSource(
        currentUser: CurrentUser,
        draft: TransactionDraft,
        classificationRuleId: Long?,
    ): ClassificationSource {
        if (classificationRuleId != null) {
            val matches = merchantClassificationRuleService.matches(
                currentUser = currentUser,
                ruleId = classificationRuleId,
                merchant = draft.merchant,
                category = draft.category,
                tags = draft.tags,
            )
            if (!matches) {
                throw ApiException(
                    ErrorCode.INVALID_CLASSIFICATION_RULE,
                    "가맹점 분류 규칙이 현재 거래와 일치하지 않습니다.",
                )
            }
            return ClassificationSource.MERCHANT_RULE
        }

        return when (draft.classificationSource) {
            ClassificationSource.USER,
            ClassificationSource.AI,
            -> draft.classificationSource
            ClassificationSource.MERCHANT_RULE,
            ClassificationSource.HISTORY,
            ClassificationSource.MIGRATION,
            -> throw ApiException(
                ErrorCode.INVALID_CLASSIFICATION_SOURCE,
                "자동 분류 출처는 서버에서 확인된 경우에만 사용할 수 있습니다.",
            )
        }
    }

    private fun throwNotFoundOrConflict(
        currentUser: CurrentUser,
        transactionId: Long,
    ): Nothing {
        if (transactionRepository.findByIdAndHouseholdId(transactionId, currentUser.householdId) == null) {
            throw ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "거래를 찾을 수 없습니다.")
        }
        throw ApiException(ErrorCode.TRANSACTION_MODIFIED, "거래가 변경되었습니다. 새로고침 후 다시 시도해 주세요.")
    }

    private fun requireHouseholdMember(
        householdId: Long,
        payerId: Long,
    ) {
        val isMember = householdMembershipRepository.existsByHouseholdIdAndUserId(householdId, payerId)
        if (!isMember) {
            throw ApiException(ErrorCode.PAYER_NOT_IN_HOUSEHOLD, "결제자는 현재 가구의 구성원이어야 합니다.")
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
            throw ApiException(ErrorCode.INVALID_PAYMENT_DETAILS, "결제수단과 카드사를 확인해 주세요.")
        }
    }
}

package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class MerchantClassificationRuleService(
    private val repository: MerchantClassificationRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionTagRepository: TransactionTagRepository,
) {
    @Transactional(readOnly = true)
    fun findRecommendation(
        currentUser: CurrentUser,
        merchant: String,
    ): MerchantClassificationRule? {
        val normalizedMerchant = normalizeMerchant(merchant)
        if (normalizedMerchant.isEmpty()) {
            return null
        }
        return repository.find(currentUser.householdId, normalizedMerchant)
    }

    @Transactional
    fun save(
        currentUser: CurrentUser,
        merchant: String,
        category: TransactionCategory,
        tags: Set<TransactionTag>,
        now: OffsetDateTime,
    ) {
        val normalizedMerchant = normalizeMerchant(merchant)
        if (normalizedMerchant.isEmpty()) {
            return
        }
        repository.upsert(
            householdId = currentUser.householdId,
            merchant = merchant,
            normalizedMerchant = normalizedMerchant,
            category = category,
            tags = tags,
            confirmedByUserId = currentUser.id,
            now = now,
        )
        transactionRepository
            .findAllMerchantRuleBackfillCandidatesByHouseholdId(currentUser.householdId)
            .filter { transaction -> normalizeMerchant(transaction.merchant) == normalizedMerchant }
            .forEach { transaction ->
                val transactionId = requireNotNull(transaction.id)
                val updated = transactionRepository.updateMerchantRuleClassificationIfUnchanged(
                    id = transactionId,
                    householdId = currentUser.householdId,
                    expectedUpdatedAt = transaction.updatedAt,
                    category = category.name,
                    updatedAt = now,
                )
                if (updated == 1) {
                    transactionTagRepository.replaceAll(transactionId, tags)
                }
            }
    }

    @Transactional(readOnly = true)
    fun matches(
        currentUser: CurrentUser,
        ruleId: Long,
        merchant: String,
        category: TransactionCategory,
        tags: Set<TransactionTag>,
    ): Boolean {
        val rule = repository.findByIdAndHouseholdId(ruleId, currentUser.householdId)
            ?: return false
        return rule.normalizedMerchant == normalizeMerchant(merchant) &&
            rule.category == category &&
            rule.tags == tags
    }
}

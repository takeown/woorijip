package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class MerchantClassificationRuleService(
    private val repository: MerchantClassificationRuleRepository,
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

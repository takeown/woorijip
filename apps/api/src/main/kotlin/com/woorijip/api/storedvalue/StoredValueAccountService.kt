package com.woorijip.api.storedvalue

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import com.woorijip.api.household.HouseholdMembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class StoredValueAccountService(
    private val repository: StoredValueAccountRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(currentUser: CurrentUser): List<StoredValueAccount> =
        repository.findAllByHouseholdId(currentUser.householdId)

    @Transactional
    fun create(
        currentUser: CurrentUser,
        ownerUserId: Long,
        name: String,
        category: StoredValueAccountCategory,
        customCategoryName: String?,
        automationKey: StoredValueAutomationKey?,
    ): StoredValueAccount {
        requireOwnerInHousehold(currentUser.householdId, ownerUserId)
        requireAutomationCategory(category, automationKey)
        requireCustomCategoryName(category, customCategoryName)
        requireAvailableAutomationKey(currentUser.householdId, ownerUserId, automationKey)
        val id = repository.create(
            householdId = currentUser.householdId,
            ownerUserId = ownerUserId,
            name = name,
            category = category,
            automationKey = automationKey,
            customCategoryName = customCategoryName,
            createdAt = OffsetDateTime.now(),
        )
        return requireNotNull(repository.findByIdAndHouseholdIdForUpdate(id, currentUser.householdId))
    }

    @Transactional
    fun update(
        currentUser: CurrentUser,
        accountId: Long,
        name: String,
        category: StoredValueAccountCategory,
        customCategoryName: String?,
        archived: Boolean,
    ): StoredValueAccount {
        val account = findForUpdate(accountId, currentUser.householdId)
        requireAutomationCategory(category, account.automationKey)
        requireCustomCategoryName(category, customCategoryName)
        if (!archived && account.archivedAt != null) {
            requireAvailableAutomationKey(
                currentUser.householdId,
                account.ownerUserId,
                account.automationKey,
            )
        }
        repository.update(
            id = account.id,
            householdId = currentUser.householdId,
            name = name,
            category = category,
            customCategoryName = customCategoryName,
            archivedAt = if (archived) account.archivedAt ?: OffsetDateTime.now() else null,
        )
        return requireNotNull(
            repository.findByIdAndHouseholdIdForUpdate(account.id, currentUser.householdId),
        )
    }

    @Transactional
    fun delete(currentUser: CurrentUser, accountId: Long) {
        val account = findForUpdate(accountId, currentUser.householdId)
        if (!account.canDelete || repository.deleteUnused(account.id, currentUser.householdId) != 1) {
            throw ApiException(
                ErrorCode.STORED_VALUE_ACCOUNT_IN_USE,
                "사용 이력이 있는 잔액은 삭제할 수 없습니다. 대신 보관해 주세요.",
            )
        }
    }

    @Transactional
    fun credit(
        currentUser: CurrentUser,
        accountId: Long,
        balanceAmount: Long,
        paidAmount: Long,
        sourceName: String?,
        occurredAt: OffsetDateTime,
    ): StoredValueAccount {
        val account = findForUpdate(accountId, currentUser.householdId)
        if (account.archivedAt != null) {
            throw ApiException(ErrorCode.INVALID_STORED_VALUE_ACCOUNT, "보관한 잔액에는 금액을 추가할 수 없습니다.")
        }
        if (paidAmount > balanceAmount) {
            throw ApiException(ErrorCode.INVALID_STORED_VALUE_ACCOUNT, "계좌 출금액은 충전·지급 금액을 넘을 수 없습니다.")
        }
        repository.addCredit(
            accountId = account.id,
            balanceAmount = balanceAmount,
            paidAmount = paidAmount,
            sourceName = sourceName,
            occurredAt = occurredAt,
            createdAt = OffsetDateTime.now(),
        )
        return requireNotNull(
            repository.findByIdAndHouseholdIdForUpdate(account.id, currentUser.householdId),
        )
    }

    @Transactional
    fun adjust(
        currentUser: CurrentUser,
        accountId: Long,
        direction: StoredValueAdjustmentDirection,
        amount: Long,
        reason: String,
        occurredAt: OffsetDateTime,
    ): StoredValueAccount {
        val account = findForUpdate(accountId, currentUser.householdId)
        if (account.archivedAt != null) {
            throw ApiException(ErrorCode.INVALID_STORED_VALUE_ACCOUNT, "보관한 잔액은 조정할 수 없습니다.")
        }
        if (direction == StoredValueAdjustmentDirection.DECREASE && account.balance < amount) {
            throw ApiException(ErrorCode.INSUFFICIENT_STORED_VALUE_BALANCE, "잔액이 부족합니다.")
        }
        repository.addAdjustment(
            accountId = account.id,
            balanceDelta = if (direction == StoredValueAdjustmentDirection.INCREASE) amount else -amount,
            reason = reason,
            occurredAt = occurredAt,
            createdAt = OffsetDateTime.now(),
        )
        return requireNotNull(
            repository.findByIdAndHouseholdIdForUpdate(account.id, currentUser.householdId),
        )
    }

    fun replaceSpend(
        householdId: Long,
        transactionId: Long,
        previousAccountId: Long?,
        accountId: Long?,
        amount: Long,
        occurredAt: OffsetDateTime,
        now: OffsetDateTime,
    ) {
        if (previousAccountId != null) {
            findForUpdate(previousAccountId, householdId)
            repository.deleteSpendByTransactionId(transactionId)
        }
        if (accountId == null) return
        val account = findForUpdate(accountId, householdId)
        if (account.archivedAt != null && accountId != previousAccountId) {
            throw ApiException(ErrorCode.INVALID_STORED_VALUE_ACCOUNT, "보관한 잔액은 새 거래에 사용할 수 없습니다.")
        }
        if (account.balance < amount) {
            throw ApiException(ErrorCode.INSUFFICIENT_STORED_VALUE_BALANCE, "잔액이 부족합니다.")
        }
        repository.addSpend(accountId, transactionId, amount, occurredAt, now)
    }

    private fun findForUpdate(accountId: Long, householdId: Long): StoredValueAccount =
        repository.findByIdAndHouseholdIdForUpdate(accountId, householdId)
            ?: throw ApiException(ErrorCode.STORED_VALUE_ACCOUNT_NOT_FOUND, "잔액을 찾을 수 없습니다.")

    private fun requireOwnerInHousehold(householdId: Long, ownerUserId: Long) {
        if (!householdMembershipRepository.existsByHouseholdIdAndUserId(householdId, ownerUserId)) {
            throw ApiException(ErrorCode.PAYER_NOT_IN_HOUSEHOLD, "잔액 소유자는 현재 가구 구성원이어야 합니다.")
        }
    }

    private fun requireAvailableAutomationKey(
        householdId: Long,
        ownerUserId: Long,
        automationKey: StoredValueAutomationKey?,
    ) {
        if (
            automationKey != null &&
            repository.findActiveByHouseholdIdAndOwnerUserIdAndAutomationKey(
                householdId,
                ownerUserId,
                automationKey,
            ) != null
        ) {
            throw ApiException(
                ErrorCode.INVALID_STORED_VALUE_ACCOUNT,
                "같은 소유자의 자동 연동 잔액이 이미 있습니다.",
            )
        }
    }

    private fun requireAutomationCategory(
        category: StoredValueAccountCategory,
        automationKey: StoredValueAutomationKey?,
    ) {
        val valid = when (automationKey) {
            StoredValueAutomationKey.ONNURI_GIFT_CERTIFICATE ->
                category == StoredValueAccountCategory.GIFT_CERTIFICATE
            StoredValueAutomationKey.PREGNANCY_VOUCHER ->
                category == StoredValueAccountCategory.VOUCHER
            null -> true
        }
        if (!valid) {
            throw ApiException(ErrorCode.INVALID_STORED_VALUE_ACCOUNT, "자동 연동 종류와 잔액 분류가 맞지 않습니다.")
        }
    }

    private fun requireCustomCategoryName(
        category: StoredValueAccountCategory,
        customCategoryName: String?,
    ) {
        val valid = if (category == StoredValueAccountCategory.OTHER) {
            customCategoryName != null
        } else {
            customCategoryName == null
        }
        if (!valid) {
            throw ApiException(
                ErrorCode.INVALID_STORED_VALUE_ACCOUNT,
                "직접 입력 분류에는 종류명이 필요합니다.",
            )
        }
    }
}

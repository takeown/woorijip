package com.woorijip.api.storedvalue

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class StoredValueAccountService(
    private val repository: StoredValueAccountRepository,
) {
    @Transactional
    fun findAll(currentUser: CurrentUser): List<StoredValueAccount> {
        repository.ensureDefaults(currentUser.householdId)
        return repository.findAllByHouseholdId(currentUser.householdId)
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
        val account = repository.findByIdAndHouseholdIdForUpdate(accountId, currentUser.householdId)
            ?: throw ApiException(ErrorCode.STORED_VALUE_ACCOUNT_NOT_FOUND, "잔액 계정을 찾을 수 없습니다.")
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
            repository.findAllByHouseholdId(currentUser.householdId).find { it.id == account.id },
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
            repository.findByIdAndHouseholdIdForUpdate(previousAccountId, householdId)
                ?: throw ApiException(ErrorCode.STORED_VALUE_ACCOUNT_NOT_FOUND, "기존 잔액 계정을 찾을 수 없습니다.")
            repository.deleteSpendByTransactionId(transactionId)
        }
        if (accountId == null) return
        val account = repository.findByIdAndHouseholdIdForUpdate(accountId, householdId)
            ?: throw ApiException(ErrorCode.STORED_VALUE_ACCOUNT_NOT_FOUND, "잔액 계정을 찾을 수 없습니다.")
        if (account.balance < amount) {
            throw ApiException(ErrorCode.INSUFFICIENT_STORED_VALUE_BALANCE, "잔액 계정의 잔액이 부족합니다.")
        }
        repository.addSpend(accountId, transactionId, amount, occurredAt, now)
    }
}

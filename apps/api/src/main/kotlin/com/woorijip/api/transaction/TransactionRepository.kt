package com.woorijip.api.transaction

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.OffsetDateTime

interface TransactionRepository : CrudRepository<Transaction, Long> {
    fun findAllByHouseholdIdOrderByOccurredAtDescIdDesc(householdId: Long): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdOrderByOccurredAtDescIdDesc(
        householdId: Long,
        payerId: Long,
    ): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdNotOrderByOccurredAtDescIdDesc(
        householdId: Long,
        payerId: Long,
    ): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdAndCardIssuerAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
        householdId: Long,
        payerId: Long,
        cardIssuer: CardIssuer,
        occurredAtFrom: OffsetDateTime,
        occurredAtTo: OffsetDateTime,
    ): List<Transaction>

    @Modifying
    @Query(
        """
        UPDATE transactions
        SET merchant = :merchant,
            amount = :amount
        WHERE id = :id
          AND household_id = :householdId
          AND payer_id = :payerId
          AND card_issuer = :cardIssuer
          AND merchant = :expectedMerchant
          AND amount = :expectedAmount
          AND occurred_at = :expectedOccurredAt
        """,
    )
    fun updateStatementDetailsIfUnchanged(
        id: Long,
        householdId: Long,
        payerId: Long,
        cardIssuer: String,
        expectedMerchant: String,
        expectedAmount: Long,
        expectedOccurredAt: OffsetDateTime,
        merchant: String,
        amount: Long,
    ): Int
}

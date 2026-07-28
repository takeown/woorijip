package com.woorijip.api.transaction

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.OffsetDateTime

interface TransactionRepository : CrudRepository<Transaction, Long> {
    fun findByIdAndHouseholdId(
        id: Long,
        householdId: Long,
    ): Transaction?

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
            amount = :amount,
            updated_at = :updatedAt
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
        updatedAt: OffsetDateTime,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE transactions
        SET payer_id = :payerId,
            merchant = :merchant,
            description = :description,
            amount = :amount,
            category = :category,
            classification_source = 'USER',
            classification_confidence = 'HIGH',
            classification_confirmed_at = :updatedAt,
            payment_method = :paymentMethod,
            card_issuer = :cardIssuer,
            occurred_at = :occurredAt,
            updated_at = :updatedAt
        WHERE id = :id
          AND household_id = :householdId
          AND updated_at = :expectedUpdatedAt
        """,
    )
    fun updateIfUnchanged(
        id: Long,
        householdId: Long,
        expectedUpdatedAt: OffsetDateTime,
        payerId: Long,
        merchant: String,
        description: String?,
        amount: Long,
        category: String,
        paymentMethod: String,
        cardIssuer: String?,
        occurredAt: OffsetDateTime,
        updatedAt: OffsetDateTime,
    ): Int

    @Modifying
    @Query(
        """
        DELETE FROM transactions
        WHERE id = :id
          AND household_id = :householdId
          AND updated_at = :expectedUpdatedAt
        """,
    )
    fun deleteIfUnchanged(
        id: Long,
        householdId: Long,
        expectedUpdatedAt: OffsetDateTime,
    ): Int
}

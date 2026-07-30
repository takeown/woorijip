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

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE household_id = :householdId
          AND (occurred_at, id) < (
              COALESCE(:cursorOccurredAt, 'infinity'::timestamptz),
              COALESCE(:cursorId, 9223372036854775807)
          )
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun findPageByHouseholdId(
        householdId: Long,
        cursorOccurredAt: OffsetDateTime?,
        cursorId: Long?,
        limit: Int,
    ): List<Transaction>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE household_id = :householdId
          AND payer_id = :payerId
          AND (occurred_at, id) < (
              COALESCE(:cursorOccurredAt, 'infinity'::timestamptz),
              COALESCE(:cursorId, 9223372036854775807)
          )
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun findPageByHouseholdIdAndPayerId(
        householdId: Long,
        payerId: Long,
        cursorOccurredAt: OffsetDateTime?,
        cursorId: Long?,
        limit: Int,
    ): List<Transaction>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE household_id = :householdId
          AND payer_id != :payerId
          AND (occurred_at, id) < (
              COALESCE(:cursorOccurredAt, 'infinity'::timestamptz),
              COALESCE(:cursorId, 9223372036854775807)
          )
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun findPageByHouseholdIdAndPayerIdNot(
        householdId: Long,
        payerId: Long,
        cursorOccurredAt: OffsetDateTime?,
        cursorId: Long?,
        limit: Int,
    ): List<Transaction>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE household_id = :householdId
          AND (
              (
                  classification_source = 'MIGRATION'
                  AND category = 'OTHER'
                  AND legacy_category IS NOT NULL
                  AND btrim(legacy_category) NOT IN (
                      '식비',
                      '주거',
                      '교통',
                      '생활',
                      '건강',
                      '여가',
                      '교육',
                      '금융·보험',
                      '금융/보험',
                      '경조사',
                      '기타'
                  )
              )
              OR (
                  classification_source = 'MERCHANT_RULE'
                  AND classification_confirmed_at IS NULL
              )
          )
        ORDER BY id ASC
        """,
    )
    fun findAllMerchantRuleBackfillCandidatesByHouseholdId(householdId: Long): List<Transaction>

    @Query(
        """
        SELECT *
        FROM transactions
        WHERE household_id = :householdId
          AND payer_id = :payerId
          AND card_issuer = :cardIssuer
          AND occurred_at >= :occurredAtFrom
          AND occurred_at < :occurredAtTo
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    fun findAllInStatementWindow(
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
        UPDATE transactions
        SET category = :category,
            classification_source = 'MERCHANT_RULE',
            classification_confidence = 'HIGH',
            classification_confirmed_at = NULL,
            updated_at = :updatedAt
        WHERE id = :id
          AND household_id = :householdId
          AND (
              (
                  classification_source = 'MIGRATION'
                  AND category = 'OTHER'
                  AND legacy_category IS NOT NULL
                  AND btrim(legacy_category) NOT IN (
                      '식비',
                      '주거',
                      '교통',
                      '생활',
                      '건강',
                      '여가',
                      '교육',
                      '금융·보험',
                      '금융/보험',
                      '경조사',
                      '기타'
                  )
              )
              OR (
                  classification_source = 'MERCHANT_RULE'
                  AND classification_confirmed_at IS NULL
              )
          )
          AND updated_at = :expectedUpdatedAt
        """,
    )
    fun updateMerchantRuleClassificationIfUnchanged(
        id: Long,
        householdId: Long,
        expectedUpdatedAt: OffsetDateTime,
        category: String,
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

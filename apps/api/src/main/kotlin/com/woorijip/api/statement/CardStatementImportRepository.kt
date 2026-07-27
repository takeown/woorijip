package com.woorijip.api.statement

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.CardIssuer
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

data class CardStatementImport(
    val id: Long,
    val householdId: Long,
    val payerId: Long,
    val cardIssuer: CardIssuer,
    val statementMonth: LocalDate,
    val createdAt: OffsetDateTime,
)

data class StoredStatementCandidate(
    val id: Long,
    val importId: Long,
    val candidate: StatementCandidate,
    val appliedTransactionId: Long?,
)

@Repository
class CardStatementImportRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun save(
        currentUser: CurrentUser,
        statement: ParsedCardStatement,
        fingerprint: String,
    ): Long {
        val importId = requireNotNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO card_statement_imports (
                    household_id,
                    payer_id,
                    card_issuer,
                    statement_month,
                    fingerprint,
                    total_count,
                    total_billed_amount,
                    adjustment_count
                )
                VALUES (
                    :householdId,
                    :payerId,
                    :cardIssuer,
                    :statementMonth,
                    :fingerprint,
                    :totalCount,
                    :totalBilledAmount,
                    :adjustmentCount
                )
                ON CONFLICT (household_id, payer_id, card_issuer, fingerprint)
                DO UPDATE SET fingerprint = EXCLUDED.fingerprint
                RETURNING id
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("householdId", currentUser.householdId)
                    .addValue("payerId", currentUser.id)
                    .addValue("cardIssuer", statement.cardIssuer.name)
                    .addValue("statementMonth", statement.statementMonth.atDay(1))
                    .addValue("fingerprint", fingerprint)
                    .addValue("totalCount", statement.totalCount)
                    .addValue("totalBilledAmount", statement.totalBilledAmount)
                    .addValue("adjustmentCount", statement.adjustments.size),
                Long::class.java,
            ),
        )

        val parameters = statement.candidates.map { candidate ->
            MapSqlParameterSource()
                .addValue("importId", importId)
                .addValue("sourceRow", candidate.sourceRow)
                .addValue("occurredOn", candidate.occurredOn)
                .addValue("cardLabel", candidate.cardLabel)
                .addValue("merchant", candidate.merchant)
                .addValue("approvedAmount", candidate.approvedAmount)
                .addValue("billedAmount", candidate.billedAmount)
                .addValue("interestAmount", candidate.interestAmount)
                .addValue("entryType", candidate.type.name)
                .addValue("installmentMonths", candidate.installmentMonths)
                .addValue("installmentSequence", candidate.installmentSequence)
                .addValue("remainingInstallments", candidate.remainingInstallments)
                .addValue("remainingPrincipal", candidate.remainingPrincipal)
        }.toTypedArray()

        if (parameters.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO card_statement_candidates (
                    import_id,
                    source_row,
                    occurred_on,
                    card_label,
                    merchant,
                    approved_amount,
                    billed_amount,
                    interest_amount,
                    entry_type,
                    installment_months,
                    installment_sequence,
                    remaining_installments,
                    remaining_principal
                )
                VALUES (
                    :importId,
                    :sourceRow,
                    :occurredOn,
                    :cardLabel,
                    :merchant,
                    :approvedAmount,
                    :billedAmount,
                    :interestAmount,
                    :entryType,
                    :installmentMonths,
                    :installmentSequence,
                    :remainingInstallments,
                    :remainingPrincipal
                )
                ON CONFLICT (import_id, source_row) DO NOTHING
                """.trimIndent(),
                parameters,
            )
        }
        return importId
    }

    fun findByIdForUpdate(
        importId: Long,
        currentUser: CurrentUser,
    ): CardStatementImport? =
        jdbcTemplate.query(
            """
            SELECT id, household_id, payer_id, card_issuer, statement_month, created_at
            FROM card_statement_imports
            WHERE id = :importId
              AND household_id = :householdId
              AND payer_id = :payerId
            FOR UPDATE
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("importId", importId)
                .addValue("householdId", currentUser.householdId)
                .addValue("payerId", currentUser.id),
            ::mapImport,
        ).singleOrNull()

    fun findCandidates(importId: Long): List<StoredStatementCandidate> =
        jdbcTemplate.query(
            """
            SELECT id,
                   import_id,
                   source_row,
                   occurred_on,
                   card_label,
                   merchant,
                   approved_amount,
                   billed_amount,
                   interest_amount,
                   entry_type,
                   installment_months,
                   installment_sequence,
                   remaining_installments,
                   remaining_principal,
                   applied_transaction_id
            FROM card_statement_candidates
            WHERE import_id = :importId
            ORDER BY source_row
            """.trimIndent(),
            mapOf("importId" to importId),
            ::mapCandidate,
        )

    fun markApplied(
        importId: Long,
        sourceRow: Int,
        transactionId: Long,
    ) {
        val updated = jdbcTemplate.update(
            """
            UPDATE card_statement_candidates
            SET applied_transaction_id = :transactionId
            WHERE import_id = :importId
              AND source_row = :sourceRow
              AND applied_transaction_id IS NULL
            """.trimIndent(),
            mapOf(
                "importId" to importId,
                "sourceRow" to sourceRow,
                "transactionId" to transactionId,
            ),
        )
        check(updated == 1)
    }

    private fun mapImport(
        resultSet: ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): CardStatementImport =
        CardStatementImport(
            id = resultSet.getLong("id"),
            householdId = resultSet.getLong("household_id"),
            payerId = resultSet.getLong("payer_id"),
            cardIssuer = CardIssuer.valueOf(resultSet.getString("card_issuer")),
            statementMonth = resultSet.getObject("statement_month", LocalDate::class.java),
            createdAt = resultSet.getObject("created_at", OffsetDateTime::class.java),
        )

    private fun mapCandidate(
        resultSet: ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): StoredStatementCandidate =
        StoredStatementCandidate(
            id = resultSet.getLong("id"),
            importId = resultSet.getLong("import_id"),
            candidate = StatementCandidate(
                sourceRow = resultSet.getInt("source_row"),
                occurredOn = resultSet.getObject("occurred_on", LocalDate::class.java),
                cardLabel = resultSet.getString("card_label"),
                merchant = resultSet.getString("merchant"),
                approvedAmount = resultSet.getLong("approved_amount"),
                billedAmount = resultSet.getLong("billed_amount"),
                interestAmount = resultSet.getLong("interest_amount"),
                type = StatementEntryType.valueOf(resultSet.getString("entry_type")),
                installmentMonths = resultSet.nullableInt("installment_months"),
                installmentSequence = resultSet.nullableInt("installment_sequence"),
                remainingInstallments = resultSet.nullableInt("remaining_installments"),
                remainingPrincipal = resultSet.nullableLong("remaining_principal"),
            ),
            appliedTransactionId = resultSet.nullableLong("applied_transaction_id"),
        )

    private fun ResultSet.nullableInt(column: String): Int? =
        getObject(column, Int::class.javaObjectType)

    private fun ResultSet.nullableLong(column: String): Long? =
        getObject(column, Long::class.javaObjectType)
}

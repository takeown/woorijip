package com.woorijip.api.statistics

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class SpendingAggregate(
    val totalAmount: Long,
    val transactionCount: Long,
)

data class SpendingBreakdown(
    val key: String,
    val label: String,
    val amount: Long,
    val transactionCount: Long,
)

@Repository
class SpendingStatisticsRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun aggregate(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): SpendingAggregate =
        requireNotNull(
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0) AS total_amount,
                       COUNT(*) AS transaction_count
                FROM transactions
                WHERE household_id = :householdId
                  AND occurred_at >= :start
                  AND occurred_at < :endExclusive
                """.trimIndent(),
                parameters(householdId, start, endExclusive),
            ) { resultSet, _ ->
                SpendingAggregate(
                    totalAmount = resultSet.getLong("total_amount"),
                    transactionCount = resultSet.getLong("transaction_count"),
                )
            },
        )

    fun byPayer(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT CAST(transactions.payer_id AS TEXT) AS item_key,
                   users.display_name AS item_label,
                   SUM(transactions.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions
            JOIN users ON users.id = transactions.payer_id
            WHERE transactions.household_id = :householdId
              AND transactions.occurred_at >= :start
              AND transactions.occurred_at < :endExclusive
            GROUP BY transactions.payer_id, users.display_name
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive),
            ::breakdown,
        )

    fun byPaymentMethod(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT payment_method AS item_key,
                   payment_method AS item_label,
                   SUM(amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions
            WHERE household_id = :householdId
              AND occurred_at >= :start
              AND occurred_at < :endExclusive
            GROUP BY payment_method
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive),
            ::breakdown,
        )

    fun byCategory(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT category AS item_key,
                   category AS item_label,
                   SUM(amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions
            WHERE household_id = :householdId
              AND occurred_at >= :start
              AND occurred_at < :endExclusive
            GROUP BY category
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive),
            ::breakdown,
        )

    private fun parameters(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("householdId", householdId)
            .addValue("start", start)
            .addValue("endExclusive", endExclusive)

    private fun breakdown(
        resultSet: java.sql.ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): SpendingBreakdown =
        SpendingBreakdown(
            key = resultSet.getString("item_key"),
            label = resultSet.getString("item_label"),
            amount = resultSet.getLong("total_amount"),
            transactionCount = resultSet.getLong("transaction_count"),
        )
}

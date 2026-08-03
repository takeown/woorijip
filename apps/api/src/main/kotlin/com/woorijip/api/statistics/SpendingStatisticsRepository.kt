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
        payerId: Long?,
    ): SpendingAggregate =
        requireNotNull(
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0) AS total_amount,
                       COUNT(*) AS transaction_count
                FROM transactions AS t
                WHERE t.household_id = :householdId
                  AND t.occurred_at >= :start
                  AND t.occurred_at < :endExclusive
                  ${payerCondition("t", payerId)}
                """.trimIndent(),
                parameters(householdId, start, endExclusive, payerId),
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
        payerId: Long?,
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
              ${payerCondition("transactions", payerId)}
            GROUP BY transactions.payer_id, users.display_name
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
            ::breakdown,
        )

    fun byPaymentMethod(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT t.payment_method AS item_key,
                   t.payment_method AS item_label,
                   SUM(t.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions AS t
            WHERE t.household_id = :householdId
              AND t.occurred_at >= :start
              AND t.occurred_at < :endExclusive
              ${payerCondition("t", payerId)}
            GROUP BY t.payment_method
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
            ::breakdown,
        )

    fun byCategory(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT t.category AS item_key,
                   t.category AS item_label,
                   SUM(t.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions AS t
            WHERE t.household_id = :householdId
              AND t.occurred_at >= :start
              AND t.occurred_at < :endExclusive
              ${payerCondition("t", payerId)}
            GROUP BY t.category
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
            ::breakdown,
        )

    fun byTag(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): List<SpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT transaction_tags.tag AS item_key,
                   transaction_tags.tag AS item_label,
                   SUM(transactions.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transaction_tags
            JOIN transactions ON transactions.id = transaction_tags.transaction_id
            WHERE transactions.household_id = :householdId
              AND transactions.occurred_at >= :start
              AND transactions.occurred_at < :endExclusive
              AND transactions.classification_confirmed_at IS NOT NULL
              ${payerCondition("transactions", payerId)}
            GROUP BY transaction_tags.tag
            ORDER BY total_amount DESC, item_label
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
            ::breakdown,
        )

    private fun parameters(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("householdId", householdId)
            .addValue("start", start)
            .addValue("endExclusive", endExclusive)
            .also { parameters ->
                if (payerId != null) parameters.addValue("payerId", payerId)
            }

    private fun payerCondition(
        tableAlias: String,
        payerId: Long?,
    ): String =
        if (payerId == null) {
            ""
        } else {
            "AND $tableAlias.payer_id = :payerId"
        }

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

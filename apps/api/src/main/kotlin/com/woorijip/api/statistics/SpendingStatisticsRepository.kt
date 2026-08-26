package com.woorijip.api.statistics

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

data class SpendingAggregate(
    val totalAmount: Long,
    val coupleLivingAmount: Long,
    val childcareAmount: Long,
    val transactionCount: Long,
)

data class SpendingBreakdown(
    val key: String,
    val label: String,
    val amount: Long,
    val transactionCount: Long,
)

data class SpendingEvidenceTransaction(
    val id: Long,
    val merchant: String,
    val amount: Long,
    val occurredAt: OffsetDateTime,
    val payerLabel: String,
)

data class SpendingDetailTransaction(
    val id: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val occurredAt: OffsetDateTime,
    val payerLabel: String,
    val paymentMethod: String,
    val category: String,
    val tags: List<String>,
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
                       COALESCE(SUM(amount) FILTER (WHERE category != 'CHILDCARE'), 0) AS couple_living_amount,
                       COALESCE(SUM(amount) FILTER (WHERE category = 'CHILDCARE'), 0) AS childcare_amount,
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
                    coupleLivingAmount = resultSet.getLong("couple_living_amount"),
                    childcareAmount = resultSet.getLong("childcare_amount"),
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

    fun byDay(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): List<DailySpendingBreakdown> =
        jdbcTemplate.query(
            """
            SELECT (t.occurred_at AT TIME ZONE 'Asia/Seoul')::date AS spending_date,
                   SUM(t.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM transactions AS t
            WHERE t.household_id = :householdId
              AND t.occurred_at >= :start
              AND t.occurred_at < :endExclusive
              ${payerCondition("t", payerId)}
            GROUP BY spending_date
            ORDER BY spending_date
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
        ) { resultSet, _ ->
            DailySpendingBreakdown(
                date = resultSet.getObject("spending_date", LocalDate::class.java),
                totalAmount = resultSet.getLong("total_amount"),
                transactionCount = resultSet.getLong("transaction_count"),
            )
        }

    fun transactions(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
    ): List<SpendingDetailTransaction> =
        jdbcTemplate.query(
            """
            SELECT t.id,
                   t.merchant,
                   t.description,
                   t.amount,
                   t.occurred_at,
                   users.display_name AS payer_label,
                   t.payment_method,
                   t.category,
                   string_agg(transaction_tags.tag, ',' ORDER BY transaction_tags.tag) AS tags
            FROM transactions AS t
            JOIN users ON users.id = t.payer_id
            LEFT JOIN transaction_tags ON transaction_tags.transaction_id = t.id
            WHERE t.household_id = :householdId
              AND t.occurred_at >= :start
              AND t.occurred_at < :endExclusive
              ${payerCondition("t", payerId)}
            GROUP BY t.id, users.display_name
            ORDER BY t.occurred_at, t.id
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId),
        ) { resultSet, _ ->
            SpendingDetailTransaction(
                id = resultSet.getLong("id"),
                merchant = resultSet.getString("merchant"),
                description = resultSet.getString("description"),
                amount = resultSet.getLong("amount"),
                occurredAt = resultSet.getObject("occurred_at", OffsetDateTime::class.java),
                payerLabel = resultSet.getString("payer_label"),
                paymentMethod = resultSet.getString("payment_method"),
                category = resultSet.getString("category"),
                tags = resultSet.getString("tags")?.split(",") ?: emptyList(),
            )
        }

    fun topTransactionsByCategory(
        householdId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        payerId: Long?,
        category: String,
        limit: Int,
    ): List<SpendingEvidenceTransaction> =
        jdbcTemplate.query(
            """
            SELECT t.id,
                   t.merchant,
                   t.amount,
                   t.occurred_at,
                   users.display_name AS payer_label
            FROM transactions AS t
            JOIN users ON users.id = t.payer_id
            WHERE t.household_id = :householdId
              AND t.occurred_at >= :start
              AND t.occurred_at < :endExclusive
              AND t.category = :category
              ${payerCondition("t", payerId)}
            ORDER BY t.amount DESC, t.occurred_at DESC, t.id DESC
            LIMIT :limit
            """.trimIndent(),
            parameters(householdId, start, endExclusive, payerId)
                .addValue("category", category)
                .addValue("limit", limit),
        ) { resultSet, _ ->
            SpendingEvidenceTransaction(
                id = resultSet.getLong("id"),
                merchant = resultSet.getString("merchant"),
                amount = resultSet.getLong("amount"),
                occurredAt = resultSet.getObject("occurred_at", OffsetDateTime::class.java),
                payerLabel = resultSet.getString("payer_label"),
            )
        }

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

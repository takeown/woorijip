package com.woorijip.api.statistics

import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionTag
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

data class SpendingAnalysisTransaction(
    val id: Long,
    val payerId: Long,
    val payerLabel: String,
    val merchant: String,
    val amount: Long,
    val occurredAt: OffsetDateTime,
    val paymentMethod: PaymentMethod,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
)

@Repository
class SpendingAnalysisRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun hasTransactions(householdId: Long): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM transactions WHERE household_id = :householdId)",
            mapOf("householdId" to householdId),
            Boolean::class.java,
        ) == true

    fun summarize(
        householdId: Long,
        currentUserId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        category: TransactionCategory?,
        payer: SpendingAnalysisPayer,
    ): List<SpendingAnalysisSummary> =
        jdbcTemplate.query(
            """
            SELECT t.category, SUM(t.amount) AS total_amount, COUNT(*) AS transaction_count
            FROM transactions AS t
            WHERE $QUERY_CONDITIONS
            GROUP BY t.category
            ORDER BY total_amount DESC, t.category
            """.trimIndent(),
            queryParameters(householdId, currentUserId, start, endExclusive, category, payer),
        ) { resultSet, _ ->
            SpendingAnalysisSummary(
                category = resultSet.getString("category"),
                totalAmount = resultSet.getLong("total_amount"),
                transactionCount = resultSet.getLong("transaction_count"),
            )
        }

    fun transactions(
        householdId: Long,
        currentUserId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        category: TransactionCategory?,
        payer: SpendingAnalysisPayer,
        limit: Int,
    ): List<SpendingAnalysisTransaction> =
        jdbcTemplate.query(
            """
            SELECT t.id,
                   t.payer_id,
                   users.display_name AS payer_label,
                   t.merchant,
                   t.amount,
                   t.occurred_at,
                   t.payment_method,
                   t.category,
                   COALESCE(string_agg(transaction_tags.tag, ',' ORDER BY transaction_tags.tag), '') AS tags
            FROM transactions AS t
            JOIN users ON users.id = t.payer_id
            LEFT JOIN transaction_tags ON transaction_tags.transaction_id = t.id
            WHERE $QUERY_CONDITIONS
            GROUP BY t.id, users.display_name
            ORDER BY t.amount DESC, t.occurred_at DESC, t.id DESC
            LIMIT :limit
            """.trimIndent(),
            queryParameters(householdId, currentUserId, start, endExclusive, category, payer).addValue("limit", limit),
        ) { resultSet, _ ->
            SpendingAnalysisTransaction(
                id = resultSet.getLong("id"),
                payerId = resultSet.getLong("payer_id"),
                payerLabel = resultSet.getString("payer_label"),
                merchant = resultSet.getString("merchant"),
                amount = resultSet.getLong("amount"),
                occurredAt = resultSet.getObject("occurred_at", OffsetDateTime::class.java),
                paymentMethod = PaymentMethod.valueOf(resultSet.getString("payment_method")),
                category = TransactionCategory.valueOf(resultSet.getString("category")),
                tags = resultSet
                    .getString("tags")
                    .split(',')
                    .filter(String::isNotBlank)
                    .map(TransactionTag::valueOf)
                    .toSet(),
            )
        }

    fun consumeDailyRequest(
        householdId: Long,
        usageDate: LocalDate,
        limit: Int,
    ): Int? =
        jdbcTemplate.query(
            """
            INSERT INTO ai_daily_usage (household_id, usage_date, spending_analysis_requests)
            VALUES (:householdId, :usageDate, 1)
            ON CONFLICT (household_id, usage_date) DO UPDATE
            SET spending_analysis_requests = ai_daily_usage.spending_analysis_requests + 1
            WHERE ai_daily_usage.spending_analysis_requests < :limit
            RETURNING spending_analysis_requests
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("householdId", householdId)
                .addValue("usageDate", usageDate)
                .addValue("limit", limit),
        ) { resultSet, _ -> resultSet.getInt("spending_analysis_requests") }
            .firstOrNull()

    fun dailyRequests(
        householdId: Long,
        usageDate: LocalDate,
    ): Int =
        jdbcTemplate.query(
            """
            SELECT spending_analysis_requests
            FROM ai_daily_usage
            WHERE household_id = :householdId
              AND usage_date = :usageDate
            """.trimIndent(),
            mapOf("householdId" to householdId, "usageDate" to usageDate),
        ) { resultSet, _ -> resultSet.getInt("spending_analysis_requests") }
            .firstOrNull()
            ?: 0

    private fun queryParameters(
        householdId: Long,
        currentUserId: Long,
        start: OffsetDateTime,
        endExclusive: OffsetDateTime,
        category: TransactionCategory?,
        payer: SpendingAnalysisPayer,
    ): MapSqlParameterSource = MapSqlParameterSource()
        .addValue("householdId", householdId)
        .addValue("currentUserId", currentUserId)
        .addValue("start", start)
        .addValue("endExclusive", endExclusive)
        .addValue("category", category?.name, java.sql.Types.VARCHAR)
        .addValue("payer", payer.name)

    private companion object {
        val QUERY_CONDITIONS: String = """
            t.household_id = :householdId
            AND t.occurred_at >= :start AND t.occurred_at < :endExclusive
            AND (:category IS NULL OR t.category = :category)
            AND (:payer = 'ALL' OR (:payer = 'ME' AND t.payer_id = :currentUserId)
                OR (:payer = 'PARTNER' AND t.payer_id != :currentUserId))
        """.trimIndent()
    }
}

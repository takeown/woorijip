package com.woorijip.api.statistics

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class SpendingQuestionUsageRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun consume(
        userId: Long,
        usageDate: LocalDate,
        dailyLimit: Int,
    ): Int? =
        jdbcTemplate
            .query(
                """
                INSERT INTO spending_question_daily_usage (user_id, usage_date, request_count)
                VALUES (:userId, :usageDate, 1)
                ON CONFLICT (user_id, usage_date)
                DO UPDATE
                SET request_count = spending_question_daily_usage.request_count + 1
                WHERE spending_question_daily_usage.request_count < :dailyLimit
                RETURNING request_count
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("usageDate", usageDate)
                    .addValue("dailyLimit", dailyLimit),
            ) { resultSet, _ -> resultSet.getInt("request_count") }
            .singleOrNull()
}

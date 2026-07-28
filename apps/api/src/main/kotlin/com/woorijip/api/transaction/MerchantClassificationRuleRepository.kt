package com.woorijip.api.transaction

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.text.Normalizer
import java.util.Locale

data class MerchantClassificationRule(
    val id: Long,
    val normalizedMerchant: String,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
)

@Repository
class MerchantClassificationRuleRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun find(
        householdId: Long,
        normalizedMerchant: String,
    ): MerchantClassificationRule? {
        val rule = jdbcTemplate.query(
            """
            SELECT id, normalized_merchant, category
            FROM merchant_classification_rules
            WHERE household_id = :householdId
              AND normalized_merchant = :normalizedMerchant
            """.trimIndent(),
            mapOf(
                "householdId" to householdId,
                "normalizedMerchant" to normalizedMerchant,
            ),
        ) { resultSet, _ ->
            MerchantClassificationRule(
                id = resultSet.getLong("id"),
                normalizedMerchant = resultSet.getString("normalized_merchant"),
                category = TransactionCategory.valueOf(resultSet.getString("category")),
                tags = emptySet(),
            )
        }.singleOrNull() ?: return null

        val tags = jdbcTemplate.query(
            """
            SELECT tag
            FROM merchant_classification_rule_tags
            WHERE rule_id = :ruleId
            ORDER BY tag
            """.trimIndent(),
            mapOf("ruleId" to rule.id),
        ) { resultSet, _ ->
            TransactionTag.valueOf(resultSet.getString("tag"))
        }.toSet()

        return rule.copy(tags = tags)
    }

    fun findByIdAndHouseholdId(
        id: Long,
        householdId: Long,
    ): MerchantClassificationRule? {
        val rule = jdbcTemplate.query(
            """
            SELECT id, normalized_merchant, category
            FROM merchant_classification_rules
            WHERE id = :id
              AND household_id = :householdId
            """.trimIndent(),
            mapOf("id" to id, "householdId" to householdId),
        ) { resultSet, _ ->
            MerchantClassificationRule(
                id = resultSet.getLong("id"),
                normalizedMerchant = resultSet.getString("normalized_merchant"),
                category = TransactionCategory.valueOf(resultSet.getString("category")),
                tags = emptySet(),
            )
        }.singleOrNull() ?: return null

        val tags = jdbcTemplate.query(
            """
            SELECT tag
            FROM merchant_classification_rule_tags
            WHERE rule_id = :ruleId
            ORDER BY tag
            """.trimIndent(),
            mapOf("ruleId" to rule.id),
        ) { resultSet, _ ->
            TransactionTag.valueOf(resultSet.getString("tag"))
        }.toSet()
        return rule.copy(tags = tags)
    }

    fun upsert(
        householdId: Long,
        merchant: String,
        normalizedMerchant: String,
        category: TransactionCategory,
        tags: Set<TransactionTag>,
        confirmedByUserId: Long,
        now: OffsetDateTime,
    ) {
        val ruleId = requireNotNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO merchant_classification_rules (
                    household_id,
                    normalized_merchant,
                    merchant_display_name,
                    category,
                    confirmed_by_user_id,
                    created_at,
                    updated_at
                )
                VALUES (
                    :householdId,
                    :normalizedMerchant,
                    :merchant,
                    :category,
                    :confirmedByUserId,
                    :now,
                    :now
                )
                ON CONFLICT (household_id, normalized_merchant)
                DO UPDATE SET
                    merchant_display_name = EXCLUDED.merchant_display_name,
                    category = EXCLUDED.category,
                    confirmed_by_user_id = EXCLUDED.confirmed_by_user_id,
                    updated_at = EXCLUDED.updated_at
                RETURNING id
                """.trimIndent(),
                mapOf(
                    "householdId" to householdId,
                    "normalizedMerchant" to normalizedMerchant,
                    "merchant" to merchant,
                    "category" to category.name,
                    "confirmedByUserId" to confirmedByUserId,
                    "now" to now,
                ),
                Long::class.java,
            ),
        )

        jdbcTemplate.update(
            "DELETE FROM merchant_classification_rule_tags WHERE rule_id = :ruleId",
            mapOf("ruleId" to ruleId),
        )
        tags.forEach { tag ->
            jdbcTemplate.update(
                """
                INSERT INTO merchant_classification_rule_tags (rule_id, tag)
                VALUES (:ruleId, :tag)
                """.trimIndent(),
                mapOf("ruleId" to ruleId, "tag" to tag.name),
            )
        }
    }
}

fun normalizeMerchant(value: String): String =
    Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, "")

private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]""")

package com.woorijip.api.storedvalue

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class StoredValueAccountRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun findAllByHouseholdId(householdId: Long): List<StoredValueAccount> =
        jdbcTemplate.query(
            """
            SELECT a.id, a.household_id, a.owner_user_id, u.display_name AS owner_display_name,
                   a.category, a.custom_category_name, a.automation_key, a.name, a.archived_at, a.created_at,
                   COALESCE(SUM(m.balance_delta), 0) AS balance,
                   COUNT(m.id) = 0 AND NOT EXISTS (
                       SELECT 1 FROM transactions AS t WHERE t.stored_value_account_id = a.id
                   ) AS can_delete
            FROM stored_value_accounts AS a
            JOIN users AS u ON u.id = a.owner_user_id
            LEFT JOIN stored_value_movements AS m ON m.account_id = a.id
            WHERE a.household_id = :householdId
            GROUP BY a.id, u.display_name
            ORDER BY a.archived_at NULLS FIRST, a.owner_user_id, a.name, a.id
            """.trimIndent(),
            mapOf("householdId" to householdId),
            ::account,
        )

    fun findByIdAndHouseholdIdForUpdate(id: Long, householdId: Long): StoredValueAccount? =
        jdbcTemplate.query(
            """
            SELECT a.id, a.household_id, a.owner_user_id, u.display_name AS owner_display_name,
                   a.category, a.custom_category_name, a.automation_key, a.name, a.archived_at, a.created_at,
                   COALESCE((
                       SELECT SUM(m.balance_delta) FROM stored_value_movements AS m WHERE m.account_id = a.id
                   ), 0) AS balance,
                   NOT EXISTS (
                       SELECT 1 FROM stored_value_movements AS m WHERE m.account_id = a.id
                   ) AND NOT EXISTS (
                       SELECT 1 FROM transactions AS t WHERE t.stored_value_account_id = a.id
                   ) AS can_delete
            FROM stored_value_accounts AS a
            JOIN users AS u ON u.id = a.owner_user_id
            WHERE a.id = :id AND a.household_id = :householdId
            FOR UPDATE
            """.trimIndent(),
            mapOf("id" to id, "householdId" to householdId),
            ::account,
        ).singleOrNull()

    fun findActiveByHouseholdIdAndOwnerUserIdAndAutomationKey(
        householdId: Long,
        ownerUserId: Long,
        automationKey: StoredValueAutomationKey,
    ): StoredValueAccount? =
        findAllByHouseholdId(householdId).singleOrNull { account ->
            account.ownerUserId == ownerUserId &&
                account.automationKey == automationKey &&
                account.archivedAt == null
        }

    fun create(
        householdId: Long,
        ownerUserId: Long,
        name: String,
        category: StoredValueAccountCategory,
        automationKey: StoredValueAutomationKey?,
        customCategoryName: String? = null,
        createdAt: OffsetDateTime,
    ): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                """
                INSERT INTO stored_value_accounts (
                    household_id, owner_user_id, category, custom_category_name,
                    automation_key, name, created_at
                ) VALUES (
                    :householdId, :ownerUserId, :category, :customCategoryName,
                    :automationKey, :name, :createdAt
                )
                RETURNING id
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("householdId", householdId)
                    .addValue("ownerUserId", ownerUserId)
                    .addValue("category", category.name)
                    .addValue("customCategoryName", customCategoryName)
                    .addValue("automationKey", automationKey?.name)
                    .addValue("name", name)
                    .addValue("createdAt", createdAt),
                Long::class.java,
            ),
        )

    fun update(
        id: Long,
        householdId: Long,
        name: String,
        category: StoredValueAccountCategory,
        customCategoryName: String?,
        archivedAt: OffsetDateTime?,
    ): Int =
        jdbcTemplate.update(
            """
            UPDATE stored_value_accounts
            SET name = :name,
                category = :category,
                custom_category_name = :customCategoryName,
                archived_at = :archivedAt
            WHERE id = :id AND household_id = :householdId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("householdId", householdId)
                .addValue("name", name)
                .addValue("category", category.name)
                .addValue("customCategoryName", customCategoryName)
                .addValue("archivedAt", archivedAt),
        )

    fun deleteUnused(id: Long, householdId: Long): Int =
        jdbcTemplate.update(
            """
            DELETE FROM stored_value_accounts AS a
            WHERE a.id = :id
              AND a.household_id = :householdId
              AND NOT EXISTS (
                  SELECT 1 FROM stored_value_movements AS m WHERE m.account_id = a.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM transactions AS t WHERE t.stored_value_account_id = a.id
              )
            """.trimIndent(),
            mapOf("id" to id, "householdId" to householdId),
        )

    fun addCredit(
        accountId: Long,
        balanceAmount: Long,
        paidAmount: Long,
        sourceName: String?,
        occurredAt: OffsetDateTime,
        createdAt: OffsetDateTime,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_movements (
                account_id, type, balance_delta, paid_amount, source_name, occurred_at, created_at
            ) VALUES (
                :accountId, 'CREDIT', :balanceAmount, :paidAmount, :sourceName, :occurredAt, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("balanceAmount", balanceAmount)
                .addValue("paidAmount", paidAmount)
                .addValue("sourceName", sourceName)
                .addValue("occurredAt", occurredAt)
                .addValue("createdAt", createdAt),
        )
    }

    fun deleteSpendByTransactionId(transactionId: Long) {
        jdbcTemplate.update(
            "DELETE FROM stored_value_movements WHERE transaction_id = :transactionId",
            mapOf("transactionId" to transactionId),
        )
    }

    fun addSpend(
        accountId: Long,
        transactionId: Long,
        amount: Long,
        occurredAt: OffsetDateTime,
        createdAt: OffsetDateTime,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_movements (
                account_id, transaction_id, type, balance_delta, occurred_at, created_at
            ) VALUES (
                :accountId, :transactionId, 'SPEND', :balanceDelta, :occurredAt, :createdAt
            )
            """.trimIndent(),
            mapOf(
                "accountId" to accountId,
                "transactionId" to transactionId,
                "balanceDelta" to -amount,
                "occurredAt" to occurredAt,
                "createdAt" to createdAt,
            ),
        )
    }

    private fun account(
        resultSet: java.sql.ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): StoredValueAccount =
        StoredValueAccount(
            id = resultSet.getLong("id"),
            householdId = resultSet.getLong("household_id"),
            ownerUserId = resultSet.getLong("owner_user_id"),
            ownerDisplayName = resultSet.getString("owner_display_name"),
            category = StoredValueAccountCategory.valueOf(resultSet.getString("category")),
            customCategoryName = resultSet.getString("custom_category_name"),
            automationKey = resultSet.getString("automation_key")?.let(StoredValueAutomationKey::valueOf),
            name = resultSet.getString("name"),
            balance = resultSet.getLong("balance"),
            archivedAt = resultSet.getObject("archived_at", OffsetDateTime::class.java),
            canDelete = resultSet.getBoolean("can_delete"),
            createdAt = resultSet.getObject("created_at", OffsetDateTime::class.java),
        )
}

package com.woorijip.api.storedvalue

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class StoredValueAccountRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun ensureDefaults(householdId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO stored_value_accounts (household_id, owner_user_id, type, name)
            SELECT membership.household_id, membership.user_id, account_type.type, account_type.name
            FROM household_memberships AS membership
            CROSS JOIN (
                VALUES ('ONNURI_GIFT_CERTIFICATE', '온누리상품권'),
                       ('PREGNANCY_VOUCHER', '임산부 바우처')
            ) AS account_type(type, name)
            WHERE membership.household_id = :householdId
            ON CONFLICT (household_id, owner_user_id, type) DO NOTHING
            """.trimIndent(),
            mapOf("householdId" to householdId),
        )
    }

    fun findAllByHouseholdId(householdId: Long): List<StoredValueAccount> =
        jdbcTemplate.query(
            """
            SELECT a.id, a.household_id, a.owner_user_id, u.display_name AS owner_display_name,
                   a.type, a.name, a.created_at,
                   COALESCE(SUM(m.balance_delta), 0) AS balance
            FROM stored_value_accounts AS a
            JOIN users AS u ON u.id = a.owner_user_id
            LEFT JOIN stored_value_movements AS m ON m.account_id = a.id
            WHERE a.household_id = :householdId
            GROUP BY a.id, u.display_name
            ORDER BY a.owner_user_id, a.type
            """.trimIndent(),
            mapOf("householdId" to householdId),
            ::account,
        )

    fun findByIdAndHouseholdIdForUpdate(id: Long, householdId: Long): StoredValueAccount? =
        jdbcTemplate.query(
            """
            SELECT a.id, a.household_id, a.owner_user_id, u.display_name AS owner_display_name,
                   a.type, a.name, a.created_at,
                   COALESCE((SELECT SUM(m.balance_delta) FROM stored_value_movements AS m WHERE m.account_id = a.id), 0)
                       AS balance
            FROM stored_value_accounts AS a
            JOIN users AS u ON u.id = a.owner_user_id
            WHERE a.id = :id AND a.household_id = :householdId
            FOR UPDATE
            """.trimIndent(),
            mapOf("id" to id, "householdId" to householdId),
            ::account,
        ).singleOrNull()

    fun findByHouseholdIdAndOwnerUserIdAndType(
        householdId: Long,
        ownerUserId: Long,
        type: StoredValueAccountType,
    ): StoredValueAccount? =
        findAllByHouseholdId(householdId).singleOrNull { account ->
            account.ownerUserId == ownerUserId && account.type == type
        }

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
            type = StoredValueAccountType.valueOf(resultSet.getString("type")),
            name = resultSet.getString("name"),
            balance = resultSet.getLong("balance"),
            createdAt = resultSet.getObject("created_at", OffsetDateTime::class.java),
        )
}

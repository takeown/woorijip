package com.woorijip.api.storedvalue

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoredValueAccountMigrationTests {
    @Test
    fun `preserves deployed member balances while adding custom account fields`() {
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway
                .configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate()

            val jdbc = JdbcTemplate(dataSource)
            val userId = assertNotNull(
                jdbc.queryForObject(
                    "INSERT INTO users (display_name) VALUES ('배우자') RETURNING id",
                    Long::class.java,
                ),
            )
            val householdId = assertNotNull(
                jdbc.queryForObject(
                    "INSERT INTO households (name) VALUES ('우리집') RETURNING id",
                    Long::class.java,
                ),
            )
            jdbc.update(
                "INSERT INTO household_memberships (household_id, user_id) VALUES (?, ?)",
                householdId,
                userId,
            )
            val accountId = assertNotNull(
                jdbc.queryForObject(
                    """
                    INSERT INTO stored_value_accounts (household_id, owner_user_id, type, name)
                    VALUES (?, ?, 'PREGNANCY_VOUCHER', '임산부 바우처')
                    RETURNING id
                    """.trimIndent(),
                    Long::class.java,
                    householdId,
                    userId,
                ),
            )
            val occurredAt = OffsetDateTime.parse("2026-08-03T12:00:00+09:00")
            jdbc.update(
                """
                INSERT INTO stored_value_movements (
                    account_id, type, balance_delta, paid_amount, occurred_at
                ) VALUES (?, 'CREDIT', 500000, 0, ?)
                """.trimIndent(),
                accountId,
                occurredAt,
            )

            Flyway.configure().dataSource(dataSource).load().migrate()

            val migrated = jdbc.queryForMap(
                """
                SELECT a.owner_user_id,
                       a.name,
                       a.category,
                       a.automation_key,
                       a.archived_at,
                       SUM(m.balance_delta) AS balance
                FROM stored_value_accounts AS a
                JOIN stored_value_movements AS m ON m.account_id = a.id
                WHERE a.id = ?
                GROUP BY a.id
                """.trimIndent(),
                accountId,
            )
            assertEquals(userId, migrated["owner_user_id"])
            assertEquals("임산부 바우처", migrated["name"])
            assertEquals("VOUCHER", migrated["category"])
            assertEquals("PREGNANCY_VOUCHER", migrated["automation_key"])
            assertNull(migrated["archived_at"])
            assertEquals(500_000L, (migrated["balance"] as Number).toLong())
        }
    }
}

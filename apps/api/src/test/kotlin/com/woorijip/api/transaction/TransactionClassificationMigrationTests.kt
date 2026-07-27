package com.woorijip.api.transaction

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TransactionClassificationMigrationTests {
    @Test
    fun `preserves legacy categories while standardizing existing transactions`() {
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            val flyway = Flyway
                .configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("7"))
                .load()
            flyway.migrate()

            val jdbc = JdbcTemplate(dataSource)
            val userId = assertNotNull(
                jdbc.queryForObject(
                    "INSERT INTO users (display_name) VALUES ('테스트') RETURNING id",
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
            val createdAt = OffsetDateTime.parse("2026-07-01T12:00:00+09:00")
            val transactionId = assertNotNull(
                jdbc.queryForObject(
                    """
                    INSERT INTO transactions (
                        household_id,
                        payer_id,
                        merchant,
                        amount,
                        category,
                        payment_method,
                        occurred_at,
                        created_at
                    )
                    VALUES (?, ?, '동물병원', 50000, '반려동물', 'CASH', ?, ?)
                    RETURNING id
                    """.trimIndent(),
                    Long::class.java,
                    householdId,
                    userId,
                    createdAt,
                    createdAt,
                ),
            )

            Flyway.configure().dataSource(dataSource).load().migrate()

            val migrated = jdbc.queryForMap(
                """
                SELECT legacy_category,
                       category,
                       classification_source,
                       classification_confidence,
                       classification_confirmed_at = created_at AS confirmed_at_created_at
                FROM transactions
                WHERE id = ?
                """.trimIndent(),
                transactionId,
            )
            assertEquals("반려동물", migrated["legacy_category"])
            assertEquals("OTHER", migrated["category"])
            assertEquals("MIGRATION", migrated["classification_source"])
            assertEquals("LOW", migrated["classification_confidence"])
            assertEquals(true, migrated["confirmed_at_created_at"])
        }
    }
}

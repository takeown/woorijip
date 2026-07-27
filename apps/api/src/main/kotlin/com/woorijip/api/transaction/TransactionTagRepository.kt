package com.woorijip.api.transaction

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TransactionTagRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun replaceAll(
        transactionId: Long,
        tags: Set<TransactionTag>,
    ) {
        jdbcTemplate.update(
            "DELETE FROM transaction_tags WHERE transaction_id = :transactionId",
            mapOf("transactionId" to transactionId),
        )
        tags.forEach { tag ->
            jdbcTemplate.update(
                """
                INSERT INTO transaction_tags (transaction_id, tag)
                VALUES (:transactionId, :tag)
                """.trimIndent(),
                mapOf(
                    "transactionId" to transactionId,
                    "tag" to tag.name,
                ),
            )
        }
    }

    fun findAllByTransactionIds(transactionIds: Collection<Long>): Map<Long, Set<TransactionTag>> {
        if (transactionIds.isEmpty()) {
            return emptyMap()
        }

        return jdbcTemplate
            .query(
                """
                SELECT transaction_id, tag
                FROM transaction_tags
                WHERE transaction_id IN (:transactionIds)
                ORDER BY transaction_id, tag
                """.trimIndent(),
                mapOf("transactionIds" to transactionIds),
            ) { resultSet, _ ->
                resultSet.getLong("transaction_id") to
                    TransactionTag.valueOf(resultSet.getString("tag"))
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, tags) -> tags.toSet() }
    }
}

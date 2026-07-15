package com.woorijip.api.transaction

import com.woorijip.api.TestcontainersConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class TransactionRepositoryTests(
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `saves and returns transactions in occurred time order`() {
        val earlier = transaction("김밥천국", "2026-07-14T12:00:00+09:00")
        val later = transaction("동네마트", "2026-07-15T19:30:00+09:00")

        val savedEarlier = transactionRepository.save(earlier)
        transactionRepository.save(later)

        assertNotNull(savedEarlier.id)
        assertEquals(
            listOf("동네마트", "김밥천국"),
            transactionRepository.findAllByOrderByOccurredAtDescIdDesc().map(Transaction::merchant),
        )
    }

    private fun transaction(
        merchant: String,
        occurredAt: String,
    ) = Transaction(
        merchant = merchant,
        amount = 8_000,
        category = "식비",
        occurredAt = OffsetDateTime.parse(occurredAt),
        createdAt = OffsetDateTime.parse("2026-07-15T20:00:00+09:00"),
    )
}

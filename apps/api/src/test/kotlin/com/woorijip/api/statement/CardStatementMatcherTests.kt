package com.woorijip.api.statement

import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.Transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CardStatementMatcherTests {
    private val matcher = CardStatementMatcher()

    @Test
    fun `matches by Seoul date approved amount and normalized merchant`() {
        val matches = matcher.match(
            candidates = listOf(candidate(4, "쿠팡(쿠페이)-쿠팡", 52_870)),
            transactions = listOf(
                transaction(
                    id = 10,
                    merchant = "쿠팡 쿠페이 쿠팡",
                    amount = 52_870,
                    occurredAt = "2026-06-08T15:30:00Z",
                ),
            ),
        )

        assertEquals(StatementMatchStatus.MATCHED, matches.single().status)
        assertEquals(listOf(10L), matches.single().transactionIds)
    }

    @Test
    fun `classifies missing mismatched and duplicate transactions`() {
        val matches = matcher.match(
            candidates = listOf(
                candidate(4, "없는 가맹점", 1_000),
                candidate(5, "금액 불일치", 2_000),
                candidate(6, "중복 가맹점", 3_000),
            ),
            transactions = listOf(
                transaction(20, "금액 불일치", 2_500),
                transaction(30, "중복 가맹점", 3_000),
                transaction(31, "중복 가맹점", 3_000),
            ),
        )

        assertEquals(StatementMatchStatus.MISSING, matches[0].status)
        assertEquals(StatementMatchStatus.MISMATCH, matches[1].status)
        assertEquals(listOf(20L), matches[1].transactionIds)
        assertEquals(StatementMatchStatus.DUPLICATE_SUSPECTED, matches[2].status)
        assertEquals(listOf(30L, 31L), matches[2].transactionIds)
    }

    @Test
    fun `matches repeated identical statement rows by occurrence count`() {
        val matches = matcher.match(
            candidates = listOf(
                candidate(4, "동일 가맹점", 5_000),
                candidate(5, "동일 가맹점", 5_000),
            ),
            transactions = listOf(
                transaction(40, "동일 가맹점", 5_000),
                transaction(41, "동일 가맹점", 5_000),
            ),
        )

        assertEquals(
            listOf(StatementMatchStatus.MATCHED, StatementMatchStatus.MATCHED),
            matches.map(StatementMatch::status),
        )
        assertEquals(listOf(40L, 41L), matches.flatMap(StatementMatch::transactionIds))
    }

    private fun candidate(
        sourceRow: Int,
        merchant: String,
        amount: Long,
    ) = StatementCandidate(
        sourceRow = sourceRow,
        occurredOn = LocalDate.of(2026, 6, 9),
        cardLabel = "비자051",
        merchant = merchant,
        approvedAmount = amount,
        billedAmount = amount,
        interestAmount = 0,
        type = StatementEntryType.PURCHASE,
        installmentMonths = null,
        installmentSequence = null,
        remainingInstallments = null,
        remainingPrincipal = null,
    )

    private fun transaction(
        id: Long,
        merchant: String,
        amount: Long,
        occurredAt: String = "2026-06-09T12:00:00+09:00",
    ) = Transaction(
        id = id,
        householdId = 1,
        payerId = 1,
        merchant = merchant,
        description = null,
        amount = amount,
        category = "테스트",
        paymentMethod = PaymentMethod.CARD,
        cardIssuer = CardIssuer.KB_KOOKMIN,
        occurredAt = OffsetDateTime.parse(occurredAt),
        createdAt = OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
    )
}

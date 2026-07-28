package com.woorijip.api.transaction

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.household.Household
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
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
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Test
    fun `saves and returns transactions in occurred time order`() {
        val (householdId, payerId) = createHouseholdMember()
        val earlier = transaction(
            householdId,
            payerId,
            "김밥천국",
            "2026-07-14T12:00:00+09:00",
            "점심 식사",
        )
        val later = transaction(householdId, payerId, "동네마트", "2026-07-15T19:30:00+09:00")

        val savedEarlier = transactionRepository.save(earlier)
        transactionRepository.save(later)

        assertNotNull(savedEarlier.id)
        assertEquals(PaymentMethod.CARD, savedEarlier.paymentMethod)
        assertEquals(CardIssuer.SHINHAN, savedEarlier.cardIssuer)
        assertEquals("점심 식사", savedEarlier.description)
        assertEquals(
            listOf("동네마트", "김밥천국"),
            transactionRepository
                .findAllByHouseholdIdOrderByOccurredAtDescIdDesc(householdId)
                .map(Transaction::merchant),
        )
    }

    @Test
    fun `finds statement window transactions in half-open range ordered by occurred time then id`() {
        val (householdId, payerId) = createHouseholdMember()
        val otherPayerId = createMember(householdId, "지은")
        val windowStart = OffsetDateTime.parse("2026-07-14T00:00:00+09:00")
        val windowEnd = OffsetDateTime.parse("2026-07-16T00:00:00+09:00")

        val atWindowStart = transactionRepository.save(
            transaction(householdId, payerId, "시작 경계", "2026-07-14T00:00:00+09:00"),
        )
        val sameTimeFirst = transactionRepository.save(
            transaction(householdId, payerId, "같은 시각 1", "2026-07-15T12:00:00+09:00"),
        )
        val sameTimeSecond = transactionRepository.save(
            transaction(householdId, payerId, "같은 시각 2", "2026-07-15T12:00:00+09:00"),
        )
        transactionRepository.save(transaction(householdId, payerId, "종료 경계", "2026-07-16T00:00:00+09:00"))
        transactionRepository.save(transaction(householdId, payerId, "기간 이전", "2026-07-13T23:59:59+09:00"))
        transactionRepository.save(transaction(householdId, otherPayerId, "다른 결제자", "2026-07-15T12:00:00+09:00"))
        transactionRepository.save(
            transaction(
                householdId,
                payerId,
                "다른 카드사",
                "2026-07-15T12:00:00+09:00",
                cardIssuer = CardIssuer.HYUNDAI,
            ),
        )

        val found = transactionRepository.findAllInStatementWindow(
            householdId = householdId,
            payerId = payerId,
            cardIssuer = CardIssuer.SHINHAN,
            occurredAtFrom = windowStart,
            occurredAtTo = windowEnd,
        )

        assertEquals(
            listOf(atWindowStart.id, sameTimeFirst.id, sameTimeSecond.id),
            found.map(Transaction::id),
        )
    }

    private fun transaction(
        householdId: Long,
        payerId: Long,
        merchant: String,
        occurredAt: String,
        description: String? = null,
        cardIssuer: CardIssuer = CardIssuer.SHINHAN,
    ) = Transaction(
        householdId = householdId,
        payerId = payerId,
        merchant = merchant,
        description = description,
        amount = 8_000,
        category = TransactionCategory.FOOD,
        paymentMethod = PaymentMethod.CARD,
        cardIssuer = cardIssuer,
        occurredAt = OffsetDateTime.parse(occurredAt),
        createdAt = OffsetDateTime.parse("2026-07-15T20:00:00+09:00"),
    )

    private fun createHouseholdMember(): Pair<Long, Long> {
        val createdAt = OffsetDateTime.parse("2026-07-15T20:00:00+09:00")
        val householdId = assertNotNull(
            householdRepository.save(Household(name = "우리집", createdAt = createdAt)).id,
        )
        return householdId to createMember(householdId, "원태")
    }

    private fun createMember(
        householdId: Long,
        displayName: String,
    ): Long {
        val createdAt = OffsetDateTime.parse("2026-07-15T20:00:00+09:00")
        val userId = assertNotNull(
            appUserRepository.save(AppUser(displayName = displayName, createdAt = createdAt)).id,
        )
        householdMembershipRepository.save(
            HouseholdMembership(
                householdId = householdId,
                userId = userId,
                createdAt = createdAt,
            ),
        )
        return userId
    }
}

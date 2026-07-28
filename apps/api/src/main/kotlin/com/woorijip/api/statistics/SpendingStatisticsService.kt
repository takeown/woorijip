package com.woorijip.api.statistics

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.transaction.TransactionCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class SpendingPeriod {
    DAY,
    WEEK,
    MONTH,
}

enum class SpendingPayer {
    ALL,
    ME,
    PARTNER,
}

data class SpendingPeriodSummary(
    val totalAmount: Long,
    val transactionCount: Long,
)

data class SpendingStatistics(
    val period: SpendingPeriod,
    val payer: SpendingPayer,
    val referenceDate: LocalDate,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val current: SpendingPeriodSummary,
    val previous: SpendingPeriodSummary,
    val amountChange: Long,
    val changeRatePercent: BigDecimal?,
    val byPayer: List<SpendingBreakdown>,
    val byPaymentMethod: List<SpendingBreakdown>,
    val byCategory: List<SpendingBreakdown>,
)

@Service
class SpendingStatisticsService(
    private val spendingStatisticsRepository: SpendingStatisticsRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Transactional(readOnly = true)
    fun find(
        currentUser: CurrentUser,
        period: SpendingPeriod,
        payer: SpendingPayer = SpendingPayer.ALL,
        referenceDate: LocalDate = LocalDate.now(SEOUL),
    ): SpendingStatistics {
        val currentRange = range(period, referenceDate)
        val previousRange = previousRange(period, currentRange.start)
        val payerId = resolvePayerId(currentUser, payer)
        val current = spendingStatisticsRepository.aggregate(
            currentUser.householdId,
            currentRange.startAt,
            currentRange.endExclusiveAt,
            payerId,
        )
        val previous = spendingStatisticsRepository.aggregate(
            currentUser.householdId,
            previousRange.startAt,
            previousRange.endExclusiveAt,
            payerId,
        )

        return SpendingStatistics(
            period = period,
            payer = payer,
            referenceDate = referenceDate,
            startDate = currentRange.start,
            endDateExclusive = currentRange.endExclusive,
            current = current.toSummary(),
            previous = previous.toSummary(),
            amountChange = current.totalAmount - previous.totalAmount,
            changeRatePercent = changeRate(current.totalAmount, previous.totalAmount),
            byPayer = spendingStatisticsRepository.byPayer(
                currentUser.householdId,
                currentRange.startAt,
                currentRange.endExclusiveAt,
                payerId,
            ),
            byPaymentMethod = spendingStatisticsRepository
                .byPaymentMethod(
                    currentUser.householdId,
                    currentRange.startAt,
                    currentRange.endExclusiveAt,
                    payerId,
                ).map { item -> item.copy(label = paymentMethodLabel(item.key)) },
            byCategory = spendingStatisticsRepository
                .byCategory(
                    currentUser.householdId,
                    currentRange.startAt,
                    currentRange.endExclusiveAt,
                    payerId,
                ).map { item ->
                    item.copy(label = TransactionCategory.valueOf(item.key).label)
                },
        )
    }

    private fun resolvePayerId(
        currentUser: CurrentUser,
        payer: SpendingPayer,
    ): Long? =
        when (payer) {
            SpendingPayer.ALL -> null
            SpendingPayer.ME -> currentUser.id
            SpendingPayer.PARTNER ->
                householdMembershipRepository
                    .findMembersByHouseholdId(currentUser.householdId)
                    .singleOrNull { member -> member.userId != currentUser.id }
                    ?.userId
                    ?: NO_PAYER_ID
        }

    private fun range(
        period: SpendingPeriod,
        referenceDate: LocalDate,
    ): DateRange {
        val start = when (period) {
            SpendingPeriod.DAY -> referenceDate
            SpendingPeriod.WEEK -> referenceDate.with(java.time.DayOfWeek.MONDAY)
            SpendingPeriod.MONTH -> referenceDate.with(TemporalAdjusters.firstDayOfMonth())
        }
        val endExclusive = when (period) {
            SpendingPeriod.DAY -> start.plusDays(1)
            SpendingPeriod.WEEK -> start.plusWeeks(1)
            SpendingPeriod.MONTH -> start.plusMonths(1)
        }
        return DateRange(start, endExclusive)
    }

    private fun previousRange(
        period: SpendingPeriod,
        currentStart: LocalDate,
    ): DateRange {
        val start = when (period) {
            SpendingPeriod.DAY -> currentStart.minusDays(1)
            SpendingPeriod.WEEK -> currentStart.minusWeeks(1)
            SpendingPeriod.MONTH -> currentStart.minusMonths(1)
        }
        return DateRange(start, currentStart)
    }

    private fun changeRate(
        currentAmount: Long,
        previousAmount: Long,
    ): BigDecimal? {
        if (previousAmount == 0L) return null
        return BigDecimal
            .valueOf(currentAmount - previousAmount)
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(previousAmount), 1, RoundingMode.HALF_UP)
    }

    private fun paymentMethodLabel(key: String): String =
        when (key) {
            "CARD" -> "카드"
            "CASH" -> "현금"
            else -> "결제수단 미지정"
        }

    private fun SpendingAggregate.toSummary(): SpendingPeriodSummary =
        SpendingPeriodSummary(totalAmount, transactionCount)

    private data class DateRange(
        val start: LocalDate,
        val endExclusive: LocalDate,
    ) {
        val startAt: OffsetDateTime = start.atStartOfDay(SEOUL).toOffsetDateTime()
        val endExclusiveAt: OffsetDateTime = endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
        const val NO_PAYER_ID: Long = -1
    }
}

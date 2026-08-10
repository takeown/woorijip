package com.woorijip.api.statistics

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionTag
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
    val coupleLivingAmount: Long,
    val childcareAmount: Long,
    val transactionCount: Long,
)

data class SpendingComparisonBreakdown(
    val key: String,
    val label: String,
    val currentAmount: Long,
    val currentTransactionCount: Long,
    val previousAmount: Long,
    val previousTransactionCount: Long,
    val amountChange: Long,
    val changeRatePercent: BigDecimal?,
)

data class MonthlySpendingSummary(
    val topCategory: SpendingBreakdown,
    val sharePercent: BigDecimal,
    val categoryAmountChange: Long,
    val categoryChangeRatePercent: BigDecimal?,
    val evidenceTransactions: List<SpendingEvidenceTransaction>,
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
    val categoryComparisons: List<SpendingComparisonBreakdown>,
    val tagComparisons: List<SpendingComparisonBreakdown>,
    val recurringSpendingChanges: List<RecurringSpendingChange>,
    val monthlySummary: MonthlySpendingSummary?,
)

@Service
class SpendingStatisticsService(
    private val spendingStatisticsRepository: SpendingStatisticsRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
    private val recurringSpendingChangeExplainer: RecurringSpendingChangeExplainer,
) {
    @Transactional(readOnly = true)
    fun find(
        currentUser: CurrentUser,
        period: SpendingPeriod,
        payer: SpendingPayer = SpendingPayer.ALL,
        referenceDate: LocalDate = LocalDate.now(SEOUL),
        includeMonthlySummary: Boolean = false,
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
        val currentCategories = spendingStatisticsRepository.byCategory(
            currentUser.householdId,
            currentRange.startAt,
            currentRange.endExclusiveAt,
            payerId,
        )
        val previousCategories = spendingStatisticsRepository.byCategory(
            currentUser.householdId,
            previousRange.startAt,
            previousRange.endExclusiveAt,
            payerId,
        )
        val currentTags = spendingStatisticsRepository.byTag(
            currentUser.householdId,
            currentRange.startAt,
            currentRange.endExclusiveAt,
            payerId,
        )
        val previousTags = spendingStatisticsRepository.byTag(
            currentUser.householdId,
            previousRange.startAt,
            previousRange.endExclusiveAt,
            payerId,
        )
        val tagComparisons = compareBreakdowns(
            currentTags,
            previousTags,
        ) { key -> tagLabel(TransactionTag.valueOf(key)) }
        val categoryComparisons = compareBreakdowns(
            currentCategories,
            previousCategories,
        ) { key -> TransactionCategory.valueOf(key).label }

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
            byCategory =
                currentCategories.map { item ->
                    item.copy(label = TransactionCategory.valueOf(item.key).label)
                },
            categoryComparisons = categoryComparisons,
            tagComparisons = tagComparisons,
            recurringSpendingChanges = recurringSpendingChangeExplainer.explain(tagComparisons),
            monthlySummary = monthlySummary(
                period = period,
                included = includeMonthlySummary,
                householdId = currentUser.householdId,
                payerId = payerId,
                range = currentRange,
                current = current,
                currentCategories = currentCategories,
                categoryComparisons = categoryComparisons,
            ),
        )
    }

    private fun monthlySummary(
        period: SpendingPeriod,
        included: Boolean,
        householdId: Long,
        payerId: Long?,
        range: DateRange,
        current: SpendingAggregate,
        currentCategories: List<SpendingBreakdown>,
        categoryComparisons: List<SpendingComparisonBreakdown>,
    ): MonthlySpendingSummary? {
        if (!included || period != SpendingPeriod.MONTH || current.totalAmount == 0L) return null
        val topCategory = currentCategories.firstOrNull() ?: return null
        val comparison = categoryComparisons.single { it.key == topCategory.key }

        return MonthlySpendingSummary(
            topCategory = topCategory.copy(label = TransactionCategory.valueOf(topCategory.key).label),
            sharePercent = BigDecimal
                .valueOf(topCategory.amount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(current.totalAmount), 1, RoundingMode.HALF_UP),
            categoryAmountChange = comparison.amountChange,
            categoryChangeRatePercent = comparison.changeRatePercent,
            evidenceTransactions = spendingStatisticsRepository.topTransactionsByCategory(
                householdId = householdId,
                start = range.startAt,
                endExclusive = range.endExclusiveAt,
                payerId = payerId,
                category = topCategory.key,
                limit = MONTHLY_EVIDENCE_LIMIT,
            ),
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
            "QR" -> "QR"
            else -> "결제수단 미지정"
        }

    private fun tagLabel(tag: TransactionTag): String =
        when (tag) {
            TransactionTag.SUBSCRIPTION -> "구독"
            TransactionTag.UTILITY -> "공과금"
            TransactionTag.RECURRING_PAYMENT -> "정기결제"
        }

    private fun compareBreakdowns(
        current: List<SpendingBreakdown>,
        previous: List<SpendingBreakdown>,
        label: (String) -> String,
    ): List<SpendingComparisonBreakdown> {
        val currentByKey = current.associateBy(SpendingBreakdown::key)
        val previousByKey = previous.associateBy(SpendingBreakdown::key)

        return (currentByKey.keys + previousByKey.keys)
            .map { key ->
                val currentItem = currentByKey[key]
                val previousItem = previousByKey[key]
                val currentAmount = currentItem?.amount ?: 0
                val previousAmount = previousItem?.amount ?: 0
                SpendingComparisonBreakdown(
                    key = key,
                    label = label(key),
                    currentAmount = currentAmount,
                    currentTransactionCount = currentItem?.transactionCount ?: 0,
                    previousAmount = previousAmount,
                    previousTransactionCount = previousItem?.transactionCount ?: 0,
                    amountChange = currentAmount - previousAmount,
                    changeRatePercent = changeRate(currentAmount, previousAmount),
                )
            }.sortedWith(
                compareByDescending<SpendingComparisonBreakdown> { it.currentAmount }
                    .thenByDescending { it.previousAmount }
                    .thenBy { it.label },
            )
    }

    private fun SpendingAggregate.toSummary(): SpendingPeriodSummary =
        SpendingPeriodSummary(totalAmount, coupleLivingAmount, childcareAmount, transactionCount)

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
        const val MONTHLY_EVIDENCE_LIMIT: Int = 3
    }
}

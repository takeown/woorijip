package com.woorijip.api.statistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringSpendingChangeExplainerTests {
    private val explainer = RecurringSpendingChangeExplainer()

    @Test
    fun `explains changed recurring spending by the largest absolute amount`() {
        val changes = explainer.explain(
            listOf(
                comparison("UTILITY", "공과금", currentAmount = 0, previousAmount = 40_000),
                comparison("SUBSCRIPTION", "구독", currentAmount = 25_000, previousAmount = 10_000),
                comparison("RECURRING_PAYMENT", "정기결제", currentAmount = 5_000, previousAmount = 0),
            ),
        )

        assertEquals(
            listOf(
                SpendingChangeDirection.ENDED,
                SpendingChangeDirection.INCREASED,
                SpendingChangeDirection.NEW,
            ),
            changes.map(RecurringSpendingChange::direction),
        )
        assertEquals(
            "선택한 기간 공과금 지출이 없어졌어요. 이전 기간에는 40,000원이었어요.",
            changes[0].message,
        )
        assertEquals(
            "선택한 기간 구독 지출이 이전 기간보다 15,000원 증가했어요.",
            changes[1].message,
        )
        assertEquals(
            "선택한 기간 정기결제 지출이 5,000원 새로 생겼어요.",
            changes[2].message,
        )
    }

    @Test
    fun `does not explain unchanged recurring spending`() {
        val changes = explainer.explain(
            listOf(
                comparison("SUBSCRIPTION", "구독", currentAmount = 10_000, previousAmount = 10_000),
            ),
        )

        assertTrue(changes.isEmpty())
    }

    private fun comparison(
        key: String,
        label: String,
        currentAmount: Long,
        previousAmount: Long,
    ): SpendingComparisonBreakdown =
        SpendingComparisonBreakdown(
            key = key,
            label = label,
            currentAmount = currentAmount,
            currentTransactionCount = if (currentAmount == 0L) 0 else 1,
            previousAmount = previousAmount,
            previousTransactionCount = if (previousAmount == 0L) 0 else 1,
            amountChange = currentAmount - previousAmount,
            changeRatePercent = null,
        )
}

package com.woorijip.api.statistics

import com.woorijip.api.transaction.TransactionTag
import org.springframework.stereotype.Component
import java.util.Locale
import kotlin.math.abs

enum class SpendingChangeDirection {
    NEW,
    INCREASED,
    DECREASED,
    ENDED,
}

data class RecurringSpendingChange(
    val tag: TransactionTag,
    val label: String,
    val direction: SpendingChangeDirection,
    val currentAmount: Long,
    val previousAmount: Long,
    val amountChange: Long,
    val message: String,
)

@Component
class RecurringSpendingChangeExplainer {
    fun explain(comparisons: List<SpendingComparisonBreakdown>): List<RecurringSpendingChange> =
        comparisons
            .filter { comparison -> comparison.amountChange != 0L }
            .sortedWith(
                compareByDescending<SpendingComparisonBreakdown> { comparison ->
                    abs(comparison.amountChange)
                }.thenBy { comparison -> comparison.label },
            ).take(MAX_CHANGES)
            .map(::toChange)

    private fun toChange(comparison: SpendingComparisonBreakdown): RecurringSpendingChange {
        val direction = direction(comparison)
        return RecurringSpendingChange(
            tag = TransactionTag.valueOf(comparison.key),
            label = comparison.label,
            direction = direction,
            currentAmount = comparison.currentAmount,
            previousAmount = comparison.previousAmount,
            amountChange = comparison.amountChange,
            message = message(comparison, direction),
        )
    }

    private fun direction(comparison: SpendingComparisonBreakdown): SpendingChangeDirection =
        when {
            comparison.previousAmount == 0L -> SpendingChangeDirection.NEW
            comparison.currentAmount == 0L -> SpendingChangeDirection.ENDED
            comparison.amountChange > 0L -> SpendingChangeDirection.INCREASED
            else -> SpendingChangeDirection.DECREASED
        }

    private fun message(
        comparison: SpendingComparisonBreakdown,
        direction: SpendingChangeDirection,
    ): String =
        when (direction) {
            SpendingChangeDirection.NEW ->
                "선택한 기간 ${comparison.label} 지출이 ${formatAmount(comparison.currentAmount)}원 새로 생겼어요."
            SpendingChangeDirection.INCREASED ->
                "선택한 기간 ${comparison.label} 지출이 이전 기간보다 " +
                    "${formatAmount(comparison.amountChange)}원 증가했어요."
            SpendingChangeDirection.DECREASED ->
                "선택한 기간 ${comparison.label} 지출이 이전 기간보다 " +
                    "${formatAmount(abs(comparison.amountChange))}원 감소했어요."
            SpendingChangeDirection.ENDED ->
                "선택한 기간 ${comparison.label} 지출이 없어졌어요. " +
                    "이전 기간에는 ${formatAmount(comparison.previousAmount)}원이었어요."
        }

    private fun formatAmount(amount: Long): String = String.format(Locale.KOREA, "%,d", amount)

    private companion object {
        const val MAX_CHANGES = 3
    }
}

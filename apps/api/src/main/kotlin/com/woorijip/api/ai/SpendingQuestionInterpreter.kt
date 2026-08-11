package com.woorijip.api.ai

import com.woorijip.api.statistics.SpendingPayer
import com.woorijip.api.statistics.SpendingPeriod
import com.woorijip.api.transaction.TransactionCategory
import java.time.LocalDate

enum class GeneratedSpendingQuestionStatus {
    ANSWERABLE,
    UNSUPPORTED,
}

enum class SpendingQuestionIntent {
    TOTAL,
    CATEGORY,
    LARGEST_TRANSACTION,
}

data class GeneratedSpendingQuestion(
    val status: GeneratedSpendingQuestionStatus,
    val intent: SpendingQuestionIntent? = null,
    val period: SpendingPeriod? = null,
    val referenceDate: String? = null,
    val payer: SpendingPayer? = null,
    val category: TransactionCategory? = null,
)

data class SpendingQuestionInterpretationContext(
    val currentUserId: Long,
    val currentDate: LocalDate,
)

fun interface SpendingQuestionInterpreter {
    fun interpret(
        question: String,
        context: SpendingQuestionInterpretationContext,
    ): GeneratedSpendingQuestion
}

class SpendingQuestionInterpretationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

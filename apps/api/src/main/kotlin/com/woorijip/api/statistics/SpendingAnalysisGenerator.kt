package com.woorijip.api.statistics

import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionTag
import java.time.LocalDate
import java.time.OffsetDateTime

enum class GeneratedSpendingAnalysisStatus {
    ANSWERED,
    UNSUPPORTED,
}

data class SpendingAnalysisRecord(
    val reference: String,
    val merchant: String,
    val amount: Long,
    val occurredDate: LocalDate,
    val payer: String,
    val paymentMethod: PaymentMethod,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
)

data class SpendingAnalysisGenerationContext(
    val currentUserId: Long,
    val currentTime: OffsetDateTime,
    val records: List<SpendingAnalysisRecord>,
    val dataLimited: Boolean,
)

data class GeneratedSpendingAnalysis(
    val status: GeneratedSpendingAnalysisStatus,
    val answer: String? = null,
    val evidenceReferences: List<String> = emptyList(),
)

fun interface SpendingAnalysisGenerator {
    fun generate(
        question: String,
        context: SpendingAnalysisGenerationContext,
    ): GeneratedSpendingAnalysis
}

class SpendingAnalysisGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

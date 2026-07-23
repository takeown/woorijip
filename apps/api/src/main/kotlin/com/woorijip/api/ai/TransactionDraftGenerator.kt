package com.woorijip.api.ai

import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import java.time.OffsetDateTime

enum class GeneratedDraftStatus {
    READY,
    NEEDS_CLARIFICATION,
    UNSUPPORTED,
}

enum class GeneratedPayer {
    ME,
    PARTNER,
}

data class GeneratedTransactionDraft(
    val status: GeneratedDraftStatus,
    val merchant: String? = null,
    val amount: Long? = null,
    val category: String? = null,
    val occurredAt: String? = null,
    val payer: GeneratedPayer? = null,
    val paymentMethod: PaymentMethod? = null,
    val cardIssuer: CardIssuer? = null,
    val message: String? = null,
)

data class TransactionDraftGenerationContext(
    val currentUserId: Long,
    val currentTime: OffsetDateTime,
)

fun interface TransactionDraftGenerator {
    fun generate(
        message: String,
        context: TransactionDraftGenerationContext,
    ): GeneratedTransactionDraft
}

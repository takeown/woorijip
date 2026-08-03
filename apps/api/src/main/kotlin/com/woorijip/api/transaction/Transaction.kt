package com.woorijip.api.transaction

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("transactions")
data class Transaction(
    @Id
    val id: Long? = null,
    val householdId: Long,
    val payerId: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val legacyCategory: String? = null,
    val category: TransactionCategory,
    val classificationSource: ClassificationSource = ClassificationSource.USER,
    val classificationConfidence: ClassificationConfidence = ClassificationConfidence.HIGH,
    val classificationConfirmedAt: OffsetDateTime? = null,
    val paymentMethod: PaymentMethod,
    val cardIssuer: CardIssuer?,
    val storedValueAccountId: Long? = null,
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime = createdAt,
    @Transient
    val tags: Set<TransactionTag> = emptySet(),
)

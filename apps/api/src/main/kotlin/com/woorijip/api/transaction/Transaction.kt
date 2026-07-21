package com.woorijip.api.transaction

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("transactions")
data class Transaction(
    @Id
    val id: Long? = null,
    val householdId: Long,
    val payerId: Long,
    val merchant: String,
    val amount: Long,
    val category: String,
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

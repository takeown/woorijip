package com.woorijip.api.storedvalue

import java.time.OffsetDateTime

enum class StoredValueAccountCategory {
    GIFT_CERTIFICATE,
    VOUCHER,
    LOCAL_CURRENCY,
    PREPAID,
    OTHER,
}

enum class StoredValueAutomationKey {
    ONNURI_GIFT_CERTIFICATE,
    PREGNANCY_VOUCHER,
}

data class StoredValueAccount(
    val id: Long,
    val householdId: Long,
    val ownerUserId: Long,
    val ownerDisplayName: String,
    val category: StoredValueAccountCategory,
    val automationKey: StoredValueAutomationKey?,
    val name: String,
    val balance: Long,
    val archivedAt: OffsetDateTime?,
    val canDelete: Boolean,
    val createdAt: OffsetDateTime,
)

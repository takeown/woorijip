package com.woorijip.api.storedvalue

import java.time.OffsetDateTime

enum class StoredValueAccountType {
    ONNURI_GIFT_CERTIFICATE,
    PREGNANCY_VOUCHER,
}

data class StoredValueAccount(
    val id: Long,
    val householdId: Long,
    val type: StoredValueAccountType,
    val name: String,
    val balance: Long,
    val createdAt: OffsetDateTime,
)

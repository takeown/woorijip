package com.woorijip.api.auth

data class CurrentUser(
    val id: Long,
    val displayName: String,
    val householdId: Long,
)

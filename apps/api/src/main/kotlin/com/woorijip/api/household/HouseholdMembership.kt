package com.woorijip.api.household

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("household_memberships")
data class HouseholdMembership(
    @Id
    val id: Long? = null,
    val householdId: Long,
    val userId: Long,
    val createdAt: OffsetDateTime,
)

package com.woorijip.api.household

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("households")
data class Household(
    @Id
    val id: Long? = null,
    val name: String,
    val createdAt: OffsetDateTime,
)

package com.woorijip.api.identity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("users")
data class AppUser(
    @Id
    val id: Long? = null,
    val displayName: String,
    val createdAt: OffsetDateTime,
)

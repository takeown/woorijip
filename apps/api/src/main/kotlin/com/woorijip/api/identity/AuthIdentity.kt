package com.woorijip.api.identity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("auth_identities")
data class AuthIdentity(
    @Id
    val id: Long? = null,
    val userId: Long,
    val provider: String,
    val providerSubject: String,
    val email: String?,
    val createdAt: OffsetDateTime,
)

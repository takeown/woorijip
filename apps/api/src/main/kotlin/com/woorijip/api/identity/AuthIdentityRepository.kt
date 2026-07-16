package com.woorijip.api.identity

import org.springframework.data.repository.CrudRepository

interface AuthIdentityRepository : CrudRepository<AuthIdentity, Long> {
    fun findByProviderAndProviderSubject(
        provider: String,
        providerSubject: String,
    ): AuthIdentity?
}

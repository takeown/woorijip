package com.woorijip.api.auth

import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import java.time.Instant

object TestOidcUsers {
    fun allowed(): OidcUser {
        val issuedAt = Instant.parse("2026-07-16T00:00:00Z")
        val idToken = OidcIdToken.withTokenValue("test-token")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .subject("google-subject-1")
            .claim("email", "first@example.com")
            .claim("email_verified", true)
            .claim("name", "첫 번째 사용자")
            .build()
        return DefaultOidcUser(
            listOf(OidcUserAuthority(idToken)),
            idToken,
        )
    }
}

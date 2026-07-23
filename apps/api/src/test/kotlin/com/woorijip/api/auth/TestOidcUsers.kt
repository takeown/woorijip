package com.woorijip.api.auth

import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import java.time.Instant

object TestOidcUsers {
    fun allowed(): OidcUser = oidcUser(
        subject = "google-subject-1",
        email = "first@example.com",
        name = "첫 번째 사용자",
    )

    fun disallowed(): OidcUser = oidcUser(
        subject = "disallowed-google-subject",
        email = "unknown@example.com",
        name = "미허용 사용자",
    )

    private fun oidcUser(
        subject: String,
        email: String,
        name: String,
    ): OidcUser {
        val issuedAt = Instant.parse("2026-07-16T00:00:00Z")
        val idToken = OidcIdToken.withTokenValue("test-token")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .subject(subject)
            .claim("email", email)
            .claim("email_verified", true)
            .claim("name", name)
            .build()
        return DefaultOidcUser(
            listOf(OidcUserAuthority(idToken)),
            idToken,
        )
    }
}

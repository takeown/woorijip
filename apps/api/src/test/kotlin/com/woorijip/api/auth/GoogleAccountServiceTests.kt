package com.woorijip.api.auth

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.identity.AuthIdentityRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
    ],
)
@Import(TestcontainersConfiguration::class)
@Transactional
class GoogleAccountServiceTests(
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val authIdentityRepository: AuthIdentityRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Test
    fun `provisions two allowed Google accounts into one household`() {
        val first = googleAccountService.provision(
            oidcUser("google-subject-1", "first@example.com", "첫 번째 사용자"),
        )
        val second = googleAccountService.provision(
            oidcUser("google-subject-2", "second@example.com", "두 번째 사용자"),
        )
        val repeated = googleAccountService.provision(
            oidcUser("google-subject-1", "first@example.com", "바뀐 이름"),
        )

        assertEquals(first.id, repeated.id)
        assertEquals(first.householdId, second.householdId)
        assertEquals(2, appUserRepository.count())
        assertEquals(2, authIdentityRepository.count())
        assertEquals(1, householdRepository.count())
        assertEquals(2, householdMembershipRepository.count())
    }

    @Test
    fun `rejects a Google account outside the allowlist`() {
        val exception = assertFailsWith<OAuth2AuthenticationException> {
            googleAccountService.provision(
                oidcUser("unknown-subject", "unknown@example.com", "미허용 사용자"),
            )
        }

        assertEquals(ACCOUNT_NOT_ALLOWED_ERROR, exception.error.errorCode)
    }

    @Test
    fun `rejects an unverified Google email`() {
        val exception = assertFailsWith<OAuth2AuthenticationException> {
            googleAccountService.provision(
                oidcUser(
                    subject = "unverified-subject",
                    email = "first@example.com",
                    name = "미인증 사용자",
                    emailVerified = false,
                ),
            )
        }

        assertEquals(ACCOUNT_NOT_ALLOWED_ERROR, exception.error.errorCode)
    }

    private fun oidcUser(
        subject: String,
        email: String,
        name: String,
        emailVerified: Boolean = true,
    ): OidcUser {
        val issuedAt = Instant.parse("2026-07-16T00:00:00Z")
        val idToken = OidcIdToken.withTokenValue("test-token-$subject")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .subject(subject)
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .claim("name", name)
            .build()
        return DefaultOidcUser(
            listOf(OidcUserAuthority(idToken)),
            idToken,
        )
    }
}

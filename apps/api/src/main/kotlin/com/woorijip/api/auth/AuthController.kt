package com.woorijip.api.auth

import com.woorijip.api.privacy.PrivacyConsentService
import com.woorijip.api.privacy.PrivacyConsentStatus
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class CurrentUserResponse(
    val id: Long,
    val displayName: String,
    val householdId: Long,
    val privacyConsent: PrivacyConsentStatus,
)

data class CsrfTokenResponse(
    val token: String,
    val headerName: String,
)

@RestController
class AuthController(
    private val privacyConsentService: PrivacyConsentService,
) {
    @GetMapping("/auth/me")
    fun me(currentUser: CurrentUser): CurrentUserResponse =
        currentUser.toResponse(privacyConsentService.status(currentUser.id))

    @GetMapping("/auth/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            token = csrfToken.token,
            headerName = csrfToken.headerName,
        )
}

private fun CurrentUser.toResponse(privacyConsent: PrivacyConsentStatus): CurrentUserResponse =
    CurrentUserResponse(
        id = id,
        displayName = displayName,
        householdId = householdId,
        privacyConsent = privacyConsent,
    )

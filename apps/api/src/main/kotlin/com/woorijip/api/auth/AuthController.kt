package com.woorijip.api.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class CurrentUserResponse(
    val id: Long,
    val displayName: String,
    val householdId: Long,
)

data class CsrfTokenResponse(
    val token: String,
    val headerName: String,
)

@RestController
class AuthController(
    private val googleAccountService: GoogleAccountService,
) {
    @GetMapping("/auth/me")
    fun me(
        @AuthenticationPrincipal oidcUser: OidcUser,
    ): CurrentUserResponse = googleAccountService.findByGoogleSubject(oidcUser.subject).toResponse()

    @GetMapping("/auth/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            token = csrfToken.token,
            headerName = csrfToken.headerName,
        )
}

private fun CurrentUser.toResponse(): CurrentUserResponse =
    CurrentUserResponse(
        id = id,
        displayName = displayName,
        householdId = householdId,
    )

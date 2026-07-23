package com.woorijip.api.auth

import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

@Component
class GoogleAccountAccessPolicy(
    private val authProperties: AuthProperties,
) {
    fun isAllowed(oidcUser: OidcUser): Boolean {
        val email = oidcUser.email?.trim()?.lowercase()
        return oidcUser.subject != null &&
            email != null &&
            oidcUser.emailVerified == true &&
            email in allowedEmails()
    }

    fun requireAllowed(oidcUser: OidcUser) {
        if (!isAllowed(oidcUser)) {
            throw accountNotAllowed()
        }
    }

    private fun allowedEmails(): Set<String> =
        authProperties.allowedGoogleEmails
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::lowercase)
            .toSet()
}

const val ACCOUNT_NOT_ALLOWED_ERROR = "account_not_allowed"

fun accountNotAllowed() = OAuth2AuthenticationException(
    OAuth2Error(ACCOUNT_NOT_ALLOWED_ERROR),
    "허용되지 않은 Google 계정입니다.",
)

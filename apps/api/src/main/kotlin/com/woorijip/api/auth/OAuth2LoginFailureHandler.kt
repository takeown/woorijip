package com.woorijip.api.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.web.util.UriComponentsBuilder

class OAuth2LoginFailureHandler(
    private val webUrl: String,
) : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val errorCode = (exception as? OAuth2AuthenticationException)?.error?.errorCode
        val authError = when (errorCode) {
            ACCOUNT_NOT_ALLOWED_ERROR -> "not_allowed"
            "authorization_request_not_found" -> "session_expired"
            else -> "oauth_failed"
        }
        val redirectUrl = UriComponentsBuilder
            .fromUriString(webUrl)
            .replaceQueryParam("authError", authError)
            .build()
            .toUriString()

        response.sendRedirect(redirectUrl)
    }
}

package com.woorijip.api.auth

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import kotlin.test.Test
import kotlin.test.assertEquals

class OAuth2LoginFailureHandlerTests {
    private val handler = OAuth2LoginFailureHandler("https://woorijip.example")

    @Test
    fun `redirects an account rejection with a specific error code`() {
        val response = handle(
            OAuth2AuthenticationException(OAuth2Error(ACCOUNT_NOT_ALLOWED_ERROR)),
        )

        assertEquals("https://woorijip.example?authError=not_allowed", response.redirectedUrl)
    }

    @Test
    fun `redirects an expired authorization request with a specific error code`() {
        val response = handle(
            OAuth2AuthenticationException(OAuth2Error("authorization_request_not_found")),
        )

        assertEquals("https://woorijip.example?authError=session_expired", response.redirectedUrl)
    }

    @Test
    fun `redirects other OAuth failures with a generic error code`() {
        val response = handle(AuthenticationServiceException("Google OAuth failed"))

        assertEquals("https://woorijip.example?authError=oauth_failed", response.redirectedUrl)
    }

    private fun handle(exception: org.springframework.security.core.AuthenticationException): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        handler.onAuthenticationFailure(MockHttpServletRequest(), response, exception)
        return response
    }
}

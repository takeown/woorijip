package com.woorijip.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AllowedGoogleAccountFilter(
    private val googleAccountAccessPolicy: GoogleAccountAccessPolicy,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val oidcUser = authentication?.principal as? OidcUser

        if (oidcUser != null && !googleAccountAccessPolicy.isAllowed(oidcUser)) {
            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            response.sendError(HttpStatus.UNAUTHORIZED.value())
            return
        }

        filterChain.doFilter(request, response)
    }
}

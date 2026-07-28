package com.woorijip.api.auth

import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Controller가 CurrentUser 파라미터를 선언하면 인증된 Google 계정으로 해석한다.
 * 허용되지 않은 계정은 OAuth2AuthenticationException을 던지므로 Spring Security의
 * ExceptionTranslationFilter가 401로 응답한다.
 */
@Component
class CurrentUserArgumentResolver(
    private val googleAccountService: GoogleAccountService,
) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == CurrentUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): CurrentUser {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val oidcUser = principal as? OidcUser ?: throw accountNotAllowed()
        return googleAccountService.findByGoogleSubject(oidcUser.subject)
    }
}

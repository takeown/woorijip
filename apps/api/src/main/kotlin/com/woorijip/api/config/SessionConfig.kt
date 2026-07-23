package com.woorijip.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import java.time.Duration

@Configuration
class SessionConfig {
    @Bean
    fun cookieSerializer(
        @Value("\${server.servlet.session.cookie.max-age}") maxAge: Duration,
        @Value("\${server.servlet.session.cookie.secure}") secure: Boolean,
    ): CookieSerializer =
        DefaultCookieSerializer().apply {
            setCookieMaxAge(maxAge.seconds.toInt())
            setSameSite("Lax")
            setUseHttpOnlyCookie(true)
            setUseSecureCookie(secure)
        }
}

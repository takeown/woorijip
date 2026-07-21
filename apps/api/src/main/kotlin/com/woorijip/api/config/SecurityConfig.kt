package com.woorijip.api.config

import com.woorijip.api.auth.AuthProperties
import com.woorijip.api.auth.WoorijipOidcUserService
import com.woorijip.api.ai.OpenAiProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

@Configuration
@EnableConfigurationProperties(AuthProperties::class, OpenAiProperties::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authProperties: AuthProperties,
        oidcUserService: WoorijipOidcUserService,
    ): SecurityFilterChain {
        val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        csrfTokenRepository.setCookiePath("/")

        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/health", "/actuator/health", "/auth/csrf", "/oauth2/**", "/login/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .cors(Customizer.withDefaults())
            .csrf { csrf -> csrf.csrfTokenRepository(csrfTokenRepository) }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userInfo -> userInfo.oidcUserService(oidcUserService) }
                    .successHandler(SimpleUrlAuthenticationSuccessHandler(authProperties.webUrl))
                    .failureHandler(
                        SimpleUrlAuthenticationFailureHandler("${authProperties.webUrl}/?authError=not_allowed"),
                    )
            }
            .logout { logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            }

        return http.build()
    }
}

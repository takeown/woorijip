package com.woorijip.api.config

import com.woorijip.api.auth.CurrentUserArgumentResolver
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}")
    private val allowedOrigins: Array<String>,
    private val currentUserArgumentResolver: CurrentUserArgumentResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserArgumentResolver)
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        listOf(
            "/auth/**",
            "/transactions/**",
            "/households/**",
            "/ai/**",
            "/statistics/**",
            "/card-statements/**",
        ).forEach { path ->
            registry
                .addMapping(path)
                .allowedOrigins(*allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN")
                .allowCredentials(true)
        }
    }
}

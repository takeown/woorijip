package com.woorijip.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}")
    private val allowedOrigins: Array<String>,
) : WebMvcConfigurer {
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
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN")
                .allowCredentials(true)
        }
    }
}

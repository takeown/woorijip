package com.woorijip.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.auth")
data class AuthProperties(
    val allowedGoogleEmails: List<String> = emptyList(),
    val bootstrapHouseholdName: String = "우리집",
    val webUrl: String = "http://localhost:3100",
)

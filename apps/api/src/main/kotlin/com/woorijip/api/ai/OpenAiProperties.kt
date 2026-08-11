package com.woorijip.api.ai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.openai")
data class OpenAiProperties(
    val apiKey: String = "",
    val model: String = "gpt-5.6-luna",
    val safetyIdentifierSecret: String = "",
    val spendingQuestionDailyLimit: Int = 20,
)

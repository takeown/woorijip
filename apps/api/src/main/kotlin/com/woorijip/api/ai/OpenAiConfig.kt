package com.woorijip.api.ai

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class OpenAiConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}

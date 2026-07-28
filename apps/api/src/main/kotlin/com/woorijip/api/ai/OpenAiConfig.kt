package com.woorijip.api.ai

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class OpenAiConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder =
        RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
            ).apply { setReadTimeout(READ_TIMEOUT) },
        )

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}

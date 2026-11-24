package com.woorijip.api.ai

import com.openai.core.Timeout
import com.woorijip.api.statistics.SpendingAnalysisTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class SpendingAnalysisAiConfig {
    @Bean
    fun spendingAnalysisChatClient(properties: OpenAiProperties): ChatClient {
        val manager = ToolCallingManager.builder()
            .maxTotalToolCalls(SpendingAnalysisTools.MAX_QUERIES)
            .maxCallsPerTool(SpendingAnalysisTools.MAX_QUERIES)
            .resolutionFallbackEnabled(false)
            .build()
        val model = OpenAiResponsesChatModel.builder()
            .options(
                OpenAiResponsesChatOptions.builder()
                    .apiKey(properties.apiKey.ifBlank { "not-configured" })
                    .model(properties.model)
                    .reasoningEffort("low")
                    .maxOutputTokens(properties.analysisMaxOutputTokens.coerceIn(100, 1_000))
                    .parallelToolCalls(false)
                    .timeout(Duration.ofSeconds(30))
                    .maxRetries(0)
                    .build(),
            )
            .httpClientBuilderCustomizer {
                it.timeout(Timeout.builder().connect(Duration.ofSeconds(5)).read(Duration.ofSeconds(30)).build())
            }
            .toolCallingManager(manager)
            .build()
        return ChatClient.builder(model)
            .defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(manager).build())
            .build()
    }
}

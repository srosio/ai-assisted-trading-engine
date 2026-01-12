package com.trading.engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SpringAiConfig {

    @Bean
    public AnthropicApi anthropicApi(final ClaudeConfig claudeConfig) {
        final var apiKey = claudeConfig.getApiKey();

        // Handle missing or mock API keys gracefully
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock-key")) {
            log.warn("No valid Claude API key configured (found: '{}'). AI analysis will fall back to static analysis.",
                    apiKey != null ? apiKey : "null");
            // Use a placeholder key - API calls will fail and trigger static fallback
            return new AnthropicApi("sk-ant-placeholder-key-will-use-static-fallback");
        }

        log.info("Initializing Anthropic API with configured API key");
        return new AnthropicApi(apiKey);
    }

    @Bean
    public AnthropicChatModel anthropicChatModel(
            final AnthropicApi anthropicApi,
            final ClaudeConfig claudeConfig) {

        final var options = AnthropicChatOptions.builder()
                .withModel(claudeConfig.getModel())
                .withMaxTokens(claudeConfig.getMaxTokens())
                .withTemperature(claudeConfig.getTemperature())
                .build();

        return new AnthropicChatModel(anthropicApi, options);
    }

    @Bean
    public ChatClient chatClient(final AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}

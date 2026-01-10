package com.trading.engine.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Bean
    public AnthropicApi anthropicApi(final ClaudeConfig claudeConfig) {
        return new AnthropicApi(claudeConfig.getApiKey());
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

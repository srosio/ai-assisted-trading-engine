package com.trading.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient claudeWebClient(ClaudeConfig claudeConfig) {
        return WebClient.builder()
                .baseUrl(claudeConfig.getApiUrl())
                .defaultHeader("x-api-key", claudeConfig.getApiKey() != null ? claudeConfig.getApiKey() : "")
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }
}

package com.trading.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "claude")
@Data
public class ClaudeConfig {

    private String apiKey;

    private String apiUrl = "https://api.anthropic.com/v1/messages";

    private String model = "claude-sonnet-4-5-20250929";

    private Integer maxTokens = 1024;

    private Double temperature = 0.3;

    private Integer timeoutSeconds = 30;
}

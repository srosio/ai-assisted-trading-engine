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

    // Primary model (Sonnet) for full analysis
    private String model = "claude-sonnet-4-5-20250929";

    // Fast model (Haiku) for pre-filtering - 90% cheaper
    private String haikuModel = "claude-haiku-4-5-20251001";

    private Integer maxTokens = 1024;

    private Double temperature = 0.3;

    private Integer timeoutSeconds = 30;

    // AI Optimization Settings
    private boolean enableSmartGating = true;  // Skip AI for low-confidence signals
    private boolean enableHaikuPrefilter = true;  // Use Haiku for initial screening
    private boolean enableCaching = true;  // Cache similar market conditions

    private int smartGatingThreshold = 60;  // Minimum confidence score for AI analysis
}

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

    private String model = "claude-3-5-sonnet-20241022";

    private Integer maxTokens = 1024;

    private Double temperature = 0.3;

    private Integer timeoutSeconds = 30;

    public static final String ANALYSIS_PROMPT_TEMPLATE = """
            You are a professional crypto market analyst.
            You do NOT give trading advice.
            You do NOT make buy/sell decisions.
            You do NOT modify risk parameters.

            Your role is LIMITED to:
            1. Assessing alignment with a liquidity sweep continuation model
            2. Identifying risks and invalidation signals
            3. Classifying setup quality (A, B, or C)

            Given the structured market context below, provide your analysis.
            Respond in strict JSON format only.

            Market context:
            %s

            Expected JSON response format:
            {
              "setup_quality": "A|B|C",
              "risk_factors": ["factor1", "factor2"],
              "invalidation": "description",
              "summary": "brief factual summary",
              "alignment_score": 0-100,
              "key_observation": "most important thing to watch"
            }
            """;
}

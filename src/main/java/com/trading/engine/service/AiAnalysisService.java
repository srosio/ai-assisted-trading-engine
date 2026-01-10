package com.trading.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Analysis Service with STRICT constraints.
 * Claude is used ONLY as a market analyst, NEVER as a trader.
 *
 * AI CANNOT:
 * - Make buy/sell decisions
 * - Modify risk parameters
 * - Override rules
 *
 * AI CAN ONLY:
 * - Assess setup quality (A/B/C)
 * - Identify risk factors
 * - Provide invalidation criteria
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final WebClient claudeWebClient;
    private final ClaudeConfig claudeConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get constrained AI analysis of market context
     */
    public AiAssessment analyzeContext(MarketContext context) {
        log.info("Requesting AI analysis for {} - Event: {}", context.getSymbol(), context.getLiquidityEvent());

        try {
            // Build the constrained prompt
            String contextJson = objectMapper.writeValueAsString(context);
            String prompt = String.format(ClaudeConfig.ANALYSIS_PROMPT_TEMPLATE, contextJson);

            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", claudeConfig.getModel());
            requestBody.put("max_tokens", claudeConfig.getMaxTokens());
            requestBody.put("temperature", claudeConfig.getTemperature());
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            // Call Claude API
            String response = claudeWebClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(claudeConfig.getTimeoutSeconds()))
                    .block();

            // Parse response
            JsonNode responseNode = objectMapper.readTree(response);
            String contentText = responseNode.get("content").get(0).get("text").asText();

            // Extract JSON from response (Claude might wrap it in markdown)
            String jsonContent = extractJson(contentText);

            // Parse AI assessment
            AiAssessment assessment = objectMapper.readValue(jsonContent, AiAssessment.class);

            // Validate assessment
            validateAssessment(assessment);

            log.info("AI Assessment complete - Quality: {}, Alignment: {}",
                    assessment.getSetupQuality(), assessment.getAlignmentScore());

            return assessment;

        } catch (Exception e) {
            log.error("Error getting AI analysis: {}", e.getMessage(), e);
            // Return fallback assessment
            return createFallbackAssessment(e.getMessage());
        }
    }

    /**
     * Extract JSON from potentially markdown-wrapped response
     */
    private String extractJson(String content) {
        // Remove markdown code blocks if present
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }

    /**
     * Validate that AI assessment follows constraints
     */
    private void validateAssessment(AiAssessment assessment) {
        if (assessment.getSetupQuality() == null ||
                !Arrays.asList("A", "B", "C").contains(assessment.getSetupQuality())) {
            throw new IllegalStateException("Invalid setup quality: " + assessment.getSetupQuality());
        }

        if (assessment.getRiskFactors() == null || assessment.getRiskFactors().isEmpty()) {
            log.warn("AI did not provide risk factors");
        }

        if (assessment.getInvalidation() == null || assessment.getInvalidation().isEmpty()) {
            log.warn("AI did not provide invalidation criteria");
        }

        // Ensure alignment score is in valid range
        if (assessment.getAlignmentScore() != null) {
            if (assessment.getAlignmentScore() < 0 || assessment.getAlignmentScore() > 100) {
                log.warn("Invalid alignment score: {}, capping to range", assessment.getAlignmentScore());
                assessment.setAlignmentScore(Math.max(0, Math.min(100, assessment.getAlignmentScore())));
            }
        } else {
            // Default alignment score based on quality
            assessment.setAlignmentScore(getDefaultAlignmentScore(assessment.getSetupQuality()));
        }
    }

    /**
     * Get default alignment score based on quality
     */
    private int getDefaultAlignmentScore(String quality) {
        return switch (quality) {
            case "A" -> 80;
            case "B" -> 60;
            case "C" -> 40;
            default -> 50;
        };
    }

    /**
     * Create fallback assessment if AI call fails
     */
    private AiAssessment createFallbackAssessment(String errorMessage) {
        log.warn("Creating fallback assessment due to AI error: {}", errorMessage);

        return AiAssessment.builder()
                .setupQuality("C")
                .riskFactors(Arrays.asList("AI analysis unavailable", "Manual review required"))
                .invalidation("Unable to determine - manual analysis needed")
                .summary("AI analysis failed. This setup requires manual review before consideration.")
                .alignmentScore(30)
                .keyObservation("System error - do not trade without manual confirmation")
                .build();
    }

    /**
     * Check if AI service is healthy
     */
    public boolean isHealthy() {
        try {
            // Simple health check - just verify we can construct a request
            return claudeConfig.getApiKey() != null && !claudeConfig.getApiKey().isEmpty();
        } catch (Exception e) {
            log.error("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}

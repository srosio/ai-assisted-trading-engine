package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final ChatClient chatClient;
    private final ClaudeConfig claudeConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiAssessment analyzeContext(final MarketContext context) {
        log.info("Requesting AI analysis for {} - Event: {}", context.getSymbol(), context.getLiquidityEvent());

        try {
            // Build the constrained prompt
            final var contextJson = objectMapper.writeValueAsString(context);
            final var prompt = String.format(ClaudeConfig.ANALYSIS_PROMPT_TEMPLATE, contextJson);

            // Create output converter for structured response
            final var outputConverter = new BeanOutputConverter<>(AiAssessment.class);

            // Call Claude API using Spring AI ChatClient
            final var assessment = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(AiAssessment.class);

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

    private void validateAssessment(final AiAssessment assessment) {
        if (assessment.getSetupQuality() == null ||
                !List.of("A", "B", "C").contains(assessment.getSetupQuality())) {
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

    private int getDefaultAlignmentScore(final String quality) {
        return switch (quality) {
            case "A" -> 80;
            case "B" -> 60;
            case "C" -> 40;
            default -> 50;
        };
    }

    private AiAssessment createFallbackAssessment(final String errorMessage) {
        log.warn("Creating fallback assessment due to AI error: {}", errorMessage);

        return AiAssessment.builder()
                .setupQuality("C")
                .riskFactors(List.of("AI analysis unavailable", "Manual review required"))
                .invalidation("Unable to determine - manual analysis needed")
                .summary("AI analysis failed. This setup requires manual review before consideration.")
                .alignmentScore(30)
                .keyObservation("System error - do not trade without manual confirmation")
                .build();
    }

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

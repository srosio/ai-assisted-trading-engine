package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.ExecutionPlan;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final ChatClient chatClient;
    private final ClaudeConfig claudeConfig;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AiAssessment analyzeContext(final MarketContext context) {
        log.info("Requesting AI analysis for {} - Event: {}", context.getSymbol(), context.getLiquidityEvent());

        try {
            // Build compact context with only essential fields
            final var compactContext = objectMapper.createObjectNode();
            compactContext.put("sym", context.getSymbol());
            compactContext.put("event", context.getLiquidityEvent());
            compactContext.put("px", context.getCurrentPrice());
            compactContext.put("htf", context.getHtfBias());
            compactContext.put("loc", context.getLocation());
            compactContext.put("sess", context.getSession());
            compactContext.put("oiChg", context.getOiChangePercent());
            compactContext.put("fund", context.getFundingRate());
            compactContext.put("vol", context.getVolatility());
            compactContext.put("volSpike", context.getVolumeSpike());
            compactContext.put("displ", context.getDisplacementDetected());

            final var contextJson = objectMapper.writeValueAsString(compactContext);
            final var outputConverter = new BeanOutputConverter<>(AiAssessment.class);

            // Concise prompt - analyst role only
            final var userMessage = String.format("""
                    Crypto analyst: Assess liquidity sweep alignment, identify risks/invalidation, classify quality (A/B/C).

                    %s

                    %s
                    """, contextJson, outputConverter.getFormat());

            final var response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            final var assessment = outputConverter.convert(response);
            validateAssessment(assessment);

            log.info("AI Assessment complete - Quality: {}, Alignment: {}",
                    assessment.getSetupQuality(), assessment.getAlignmentScore());

            return assessment;

        } catch (final Exception e) {
            log.error("Error getting AI analysis: {}", e.getMessage(), e);
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

        if (assessment.getAlignmentScore() != null) {
            if (assessment.getAlignmentScore() < 0 || assessment.getAlignmentScore() > 100) {
                log.warn("Invalid alignment score: {}, capping to range", assessment.getAlignmentScore());
                assessment.setAlignmentScore(Math.max(0, Math.min(100, assessment.getAlignmentScore())));
            }
        } else {
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

    public ExecutionPlan generateExecutionPlan(final MarketContext context,
                                                final IntradayContext intradayContext,
                                                final String direction) {
        log.info("Generating execution plan for {} - Direction: {}", context.getSymbol(), direction);

        try {
            // Build compact context with essential fields only
            final var ctx = objectMapper.createObjectNode();
            ctx.put("sym", context.getSymbol());
            ctx.put("dir", direction);
            ctx.put("px", context.getCurrentPrice());
            ctx.put("event", context.getLiquidityEvent());
            ctx.put("sess", context.getSession());
            ctx.put("htf", context.getHtfBias());
            ctx.put("vol", context.getVolatility());
            ctx.put("fund", context.getFundingRate());

            // Intraday context (compact keys)
            ctx.put("t15", intradayContext.getTrendBias15m());
            ctx.put("t5", intradayContext.getTrendBias5m());
            ctx.put("oiPx", intradayContext.getOiPriceBehavior());
            ctx.put("volConf", intradayContext.getVolumeConfirmation());
            ctx.put("narrative", intradayContext.getSessionNarrative());
            ctx.put("conf", intradayContext.getConfidenceScore());

            final var contextJson = objectMapper.writeValueAsString(ctx);
            final var outputConverter = new BeanOutputConverter<>(ExecutionPlan.class);

            // Concise execution planner prompt
            final var userMessage = String.format("""
                    Plan execution: model (market/limit/scale-in), entry zone, stop logic+price, targets (R multiples), invalidations, risks.

                    %s

                    %s
                    """, contextJson, outputConverter.getFormat());

            final var response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            final var executionPlan = outputConverter.convert(response);

            log.info("Execution plan generated - Model: {}, Targets: {}",
                    executionPlan.getExecutionModel(),
                    executionPlan.getTargets() != null ? executionPlan.getTargets().size() : 0);

            return executionPlan;

        } catch (final Exception e) {
            log.error("Error generating execution plan: {}", e.getMessage(), e);
            return createFallbackExecutionPlan(context, direction);
        }
    }

    /**
     * Create fallback execution plan when AI fails
     */
    private ExecutionPlan createFallbackExecutionPlan(final MarketContext context, final String direction) {
        log.warn("Creating fallback execution plan");

        final var currentPrice = context.getCurrentPrice();

        return ExecutionPlan.builder()
                .executionModel("manual")
                .entryZoneLow(currentPrice)
                .entryZoneHigh(currentPrice)
                .stopLogic("Manual - AI unavailable")
                .suggestedStopPrice(currentPrice)
                .targets(List.of())
                .invalidationConditions(List.of("Manual review required - AI system unavailable"))
                .riskNotes(List.of("AI execution plan unavailable", "Human trader must plan manually"))
                .executionNotes("Fallback plan - manual execution required")
                .build();
    }

    public boolean isHealthy() {
        try {
            return claudeConfig.getApiKey() != null && !claudeConfig.getApiKey().isEmpty();
        } catch (final Exception e) {
            log.error("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }
}

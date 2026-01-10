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
            // Build the constrained prompt - no curly braces to avoid template parsing
            final var contextJson = objectMapper.writeValueAsString(context);

            // BeanOutputConverter automatically adds format instructions
            final var outputConverter = new BeanOutputConverter<>(AiAssessment.class);

            // Build user message without JSON format example (let BeanOutputConverter handle it)
            final var userMessage = String.format("""
                    You are a professional crypto market analyst.
                    You do NOT give trading advice.
                    You do NOT make buy/sell decisions.
                    You do NOT modify risk parameters.

                    Your role is LIMITED to:
                    1. Assessing alignment with a liquidity sweep continuation model
                    2. Identifying risks and invalidation signals
                    3. Classifying setup quality (A, B, or C)

                    Given the structured market context below, provide your analysis.

                    Market context:
                    %s

                    %s
                    """, contextJson, outputConverter.getFormat());

            // Call Claude API using Spring AI ChatClient
            final var response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            // Parse response using the converter
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

    /**
     * Generate execution plan using AI based on market context and intraday analysis
     */
    public ExecutionPlan generateExecutionPlan(final MarketContext context,
                                                final IntradayContext intradayContext,
                                                final String direction) {
        log.info("Generating execution plan for {} - Direction: {}", context.getSymbol(), direction);

        try {
            // Build comprehensive context JSON
            final var contextData = objectMapper.createObjectNode();
            contextData.put("symbol", context.getSymbol());
            contextData.put("event", context.getLiquidityEvent());
            contextData.put("direction", direction);
            contextData.put("currentPrice", context.getCurrentPrice());
            contextData.put("session", context.getSession());
            contextData.put("htfBias", context.getHtfBias());
            contextData.put("oiChangePercent", context.getOiChangePercent());
            contextData.put("fundingRate", context.getFundingRate());
            contextData.put("volatility", context.getVolatility());

            // Add intraday context
            contextData.put("trendBias15m", intradayContext.getTrendBias15m());
            contextData.put("trendBias5m", intradayContext.getTrendBias5m());
            contextData.put("oiPriceBehavior", intradayContext.getOiPriceBehavior());
            contextData.put("volumeConfirmation", intradayContext.getVolumeConfirmation());
            contextData.put("sessionNarrative", intradayContext.getSessionNarrative());
            contextData.put("confidenceScore", intradayContext.getConfidenceScore());
            contextData.put("fundingDelta", intradayContext.getFundingRateDelta());
            contextData.put("orderBookImbalance", intradayContext.getOrderBookImbalance());

            final var contextJson = objectMapper.writeValueAsString(contextData);

            final var outputConverter = new BeanOutputConverter<>(ExecutionPlan.class);

            final var userMessage = String.format("""
                    You are a professional intraday crypto trade execution planner.

                    Your role:
                    1. Design an execution plan (market/limit/scale-in)
                    2. Define entry zone (price range)
                    3. Provide stop logic and suggested price
                    4. Define targets with R multiples
                    5. List invalidation conditions
                    6. Note risk factors (funding extremes, news, etc.)

                    DO NOT make the trade decision (already made).
                    DO NOT suggest position size.

                    Market and intraday context:
                    %s

                    Generate a structured execution plan following the required format.

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

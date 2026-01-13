package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.ExecutionPlan;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.TradingViewWebhook;
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
    private final StaticAnalysisService staticAnalysis;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AiAssessment analyzeContext(final MarketContext context,
                                        final TradingViewWebhook webhook,
                                        final IntradayContext intradayContext) {
        log.info("Requesting AI analysis for {} - Strategy: {}, Event: {}, Direction: {}",
                context.getSymbol(), webhook.getStrategy(), webhook.getEventType(), webhook.getDirection());

        try {
            // Build compact context with Universal Schema event data
            final var compactContext = objectMapper.createObjectNode();

            // Universal schema event data (primary analysis focus)
            compactContext.put("strategy", webhook.getStrategy());
            compactContext.put("eventType", webhook.getEventType());
            compactContext.put("direction", webhook.getDirection());
            compactContext.put("tf", webhook.getTimeframe());
            compactContext.put("sweptLevel", webhook.getSweptLevel());
            compactContext.put("volSpike", webhook.getVolumeSpike());
            compactContext.put("displ", webhook.getDisplacement());
            compactContext.put("suggestedSL", webhook.getSuggestedStopLoss());

            // Market context (supporting data)
            compactContext.put("sym", context.getSymbol());
            compactContext.put("px", context.getCurrentPrice());
            compactContext.put("htf", context.getHtfBias());
            compactContext.put("sess", context.getSession());
            compactContext.put("oiChg", context.getOiChangePercent());
            compactContext.put("fund", context.getFundingRate());
            compactContext.put("vol", context.getVolatility());

            final var contextJson = objectMapper.writeValueAsString(compactContext);
            final var outputConverter = new BeanOutputConverter<>(AiAssessment.class);

            // Strategy-specific prompt based on strategy name and event type
            final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());
            final var userMessage = "Analyze " + webhook.getStrategy() + " strategy alert:\n\n" +
                    "Event Type: " + webhook.getEventType() + " (" + webhook.getDirection() + ")\n" +
                    "Strategy Profile: " + strategyContext.get("profile") + "\n" +
                    "Expected Criteria: " + strategyContext.get("expected") + "\n\n" +
                    "Assess setup quality based on event type, volume/displacement, swept levels, and market context.\n" +
                    "Identify risks that could reduce win rate below expected %.\n" +
                    "Classify: A (all criteria met), B (good but minor concerns), C (reject).\n\n" +
                    "Event Data: " + contextJson + "\n\n" +
                    outputConverter.getFormat();

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
            log.warn("AI unavailable, using static analysis: {}", e.getMessage());
            return staticAnalysis.generateStaticAssessment(context, intradayContext, webhook);
        }
    }

    /**
     * Get strategy-specific context based on strategy name and event type.
     * Supports universal schema with extensible strategy profiles.
     */
    private java.util.Map<String, String> getStrategyContextByName(final String strategy, final String eventType) {
        if (strategy == null || strategy.isEmpty()) {
            return getDefaultStrategyContext();
        }

        final var strategyLower = strategy.toLowerCase();
        final var eventLower = eventType != null ? eventType.toLowerCase() : "";

        // === Known Strategies ===

        // Liquidity Sweeps Strategy (45-55% WR, 1:3-1:5 R:R)
        if (strategyLower.contains("liquidity sweep")) {
            return java.util.Map.of(
                "name", "Liquidity Sweeps",
                "profile", "45-55% WR, 1:3-1:5 R:R, liquidity grab reversal",
                "expected", "Equal highs/lows swept, displacement reversal, volume confirmation, clear invalidation"
            );
        }

        // Candle 2 Closure with RSI Strategy (60-70% WR, 1:2-1:3 R:R)
        if (strategyLower.contains("candle 2") || strategyLower.contains("closure") ||
            (strategyLower.contains("rsi") && eventLower.contains("reversal"))) {
            return java.util.Map.of(
                "name", "Candle 2 Closure with RSI",
                "profile", "60-70% WR, 1:2-1:3 R:R, two-candle reversal pattern",
                "expected", "RSI extreme (>70 or <30), two consecutive candles closing against trend, volume confirmation"
            );
        }

        // === Generic Event Type Profiles (for new/unknown strategies) ===

        // Reversal events
        if (eventLower.contains("reversal")) {
            return java.util.Map.of(
                "name", strategy + " (Reversal)",
                "profile", "50-60% WR, 1:2-1:4 R:R, counter-trend reversal",
                "expected", "Clear reversal pattern, volume confirmation, divergence or exhaustion signals"
            );
        }

        // Continuation events
        if (eventLower.contains("continuation")) {
            return java.util.Map.of(
                "name", strategy + " (Continuation)",
                "profile", "55-65% WR, 1:2-1:3 R:R, trend continuation",
                "expected", "Strong trend, pullback to key level, resumption with volume"
            );
        }

        // Breakout events
        if (eventLower.contains("breakout")) {
            return java.util.Map.of(
                "name", strategy + " (Breakout)",
                "profile", "30-40% WR, 1:5-1:10+ R:R, explosive range break",
                "expected", "Tight consolidation, volume surge on break, momentum confirmation"
            );
        }

        // Sweep events
        if (eventLower.contains("sweep")) {
            return java.util.Map.of(
                "name", strategy + " (Sweep)",
                "profile", "45-55% WR, 1:3-1:5 R:R, liquidity grab",
                "expected", "Key level swept, immediate reversal, displacement confirmation"
            );
        }

        // === Default/Unknown Strategy ===
        return getDefaultStrategyContext();
    }

    /**
     * Default strategy context for unknown/legacy strategies
     */
    private java.util.Map<String, String> getDefaultStrategyContext() {
        return java.util.Map.of(
            "name", "Generic Strategy",
            "profile", "40-60% WR, 1:2-1:4 R:R, general trading setup",
            "expected", "Clear setup structure, volume/price confirmation, defined risk parameters"
        );
    }

    /**
     * Legacy method for backward compatibility with old event-based analysis
     * @deprecated Use getStrategyContextByName instead
     */
    @Deprecated
    private java.util.Map<String, String> getStrategyContext(final String event) {
        final var eventLower = event != null ? event.toLowerCase() : "";

        // Try to extract strategy from legacy event string
        if (eventLower.contains("liquidity") || eventLower.contains("sweep")) {
            return getStrategyContextByName("Liquidity Sweeps", "sweep");
        }
        if (eventLower.contains("closure") || eventLower.contains("candle_2")) {
            return getStrategyContextByName("Candle 2 Closure RSI", "reversal");
        }
        if (eventLower.contains("breakout")) {
            return getStrategyContextByName("Unknown", "breakout");
        }
        if (eventLower.contains("reversal")) {
            return getStrategyContextByName("Unknown", "reversal");
        }

        return getDefaultStrategyContext();
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
                                                final TradingViewWebhook webhook,
                                                final String direction) {
        log.info("Generating execution plan for {} - Strategy: {}, Event: {}, Direction: {}",
                context.getSymbol(), webhook.getStrategy(), webhook.getEventType(), direction);

        try {
            // Build compact context with Universal Schema event data and market context
            final var ctx = objectMapper.createObjectNode();

            // Universal schema event data
            ctx.put("strategy", webhook.getStrategy());
            ctx.put("eventType", webhook.getEventType());
            ctx.put("direction", webhook.getDirection());
            ctx.put("tf", webhook.getTimeframe());
            ctx.put("sweptLevel", webhook.getSweptLevel());
            ctx.put("suggestedSL", webhook.getSuggestedStopLoss());

            // Market context
            ctx.put("sym", context.getSymbol());
            ctx.put("dir", direction);
            ctx.put("px", context.getCurrentPrice());
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

            // Strategy-specific execution planning
            final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());
            final var userMessage = "Plan execution for " + webhook.getStrategy() + " strategy:\n\n" +
                    "Event: " + webhook.getEventType() + " (" + webhook.getDirection() + ")\n" +
                    "Target Profile: " + strategyContext.get("profile") + "\n" +
                    "Expected Criteria: " + strategyContext.get("expected") + "\n\n" +
                    "Generate execution plan: entry model, entry zone, stop loss, targets (match expected R:R), invalidations.\n\n" +
                    "Event-specific guidance:\n" +
                    "- Reversal: Entry after confirmation, stop beyond reversal point, 2-4R targets\n" +
                    "- Continuation: Entry on pullback, stop beyond structure, 2-3R targets\n" +
                    "- Breakout: Entry on break confirmation, stop inside range, 5-10R+ targets\n" +
                    "- Sweep: Entry beyond swept level, stop past sweep invalidation, 3-5R targets\n\n" +
                    "Context: " + contextJson + "\n\n" +
                    outputConverter.getFormat();

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
            log.warn("AI unavailable for execution plan, using static analysis: {}", e.getMessage());
            final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());
            return staticAnalysis.generateStaticExecutionPlan(
                    context, intradayContext, webhook, direction, strategyContext.get("name")
            );
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

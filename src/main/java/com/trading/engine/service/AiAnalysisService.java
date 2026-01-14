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
        log.info("Requesting AI analysis for {} - Strategy: {}, Event: {}",
                context.getSymbol(), webhook.getStrategy(), webhook.getEventType());

        try {
            // Build compact context with strategy event data
            final var compactContext = objectMapper.createObjectNode();

            // Strategy event data (primary analysis focus)
            compactContext.put("strategy", webhook.getStrategy());
            compactContext.put("eventType", webhook.getEventType());
            compactContext.put("direction", webhook.getDirection());
            compactContext.put("tf", webhook.getTimeframe());
            compactContext.put("session", webhook.getSession());

            // Context fields (if available)
            if (webhook.getSweptLevel() != null) {
                compactContext.put("sweptLevel", webhook.getSweptLevel());
            }
            if (webhook.getHtfBias() != null) {
                compactContext.put("htfBias", webhook.getHtfBias());
            }
            if (webhook.getDisplacement() != null) {
                compactContext.put("displacement", webhook.getDisplacement());
            }
            if (webhook.getVolumeSpike() != null) {
                compactContext.put("volumeSpike", webhook.getVolumeSpike());
            }

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

            // Strategy-specific prompt based on strategy name
            final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());
            final var userMessage = "Analyze " + strategyContext.get("name") + " strategy alert:\n\n" +
                    "Strategy profile: " + strategyContext.get("profile") + "\n" +
                    "Expected: " + strategyContext.get("expected") + "\n\n" +
                    "Assess setup quality based on event type (" + webhook.getEventType() +
                    "), direction (" + webhook.getDirection() + "), and market context.\n" +
                    "Identify risks that could reduce win rate below expected %.\n" +
                    "Classify: A (all criteria met), B (good but minor concerns), C (reject).\n\n" +
                    contextJson + "\n\n" +
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
     * Get strategy-specific context based on strategy name and event type
     */
    private java.util.Map<String, String> getStrategyContextByName(final String strategy, final String eventType) {
        if (strategy == null) {
            return getDefaultStrategyContext(eventType);
        }

        final var strategyLower = strategy.toLowerCase();

        // Liquidity Sweeps Strategy (45-55% WR, 1:3-1:5 R:R)
        if (strategyLower.contains("liquidity") || strategyLower.contains("sweep")) {
            return java.util.Map.of(
                "name", "Liquidity Sweeps",
                "profile", "45-55% WR, 1:3-1:5 R:R, liquidity grab reversal",
                "expected", "Equal highs/lows swept, displacement reversal, volume confirmation"
            );
        }

        // Candle 2 Closure with RSI Strategy (60-70% WR, 1:2-1:3 R:R)
        if (strategyLower.contains("candle") || strategyLower.contains("closure") || strategyLower.contains("rsi")) {
            return java.util.Map.of(
                "name", "Candle 2 Closure with RSI",
                "profile", "60-70% WR, 1:2-1:3 R:R, two-candle reversal pattern",
                "expected", "RSI extreme (>70 or <30), two consecutive candles closing against trend"
            );
        }

        // Breakout Strategy (30-40% WR, 1:5-1:10+ R:R)
        if (strategyLower.contains("breakout") || strategyLower.contains("consolidation")) {
            return java.util.Map.of(
                "name", "Breakout Strategy",
                "profile", "30-40% WR, 1:5-1:10+ R:R, explosive moves",
                "expected", "Tight consolidation, volume surge, momentum confirmation"
            );
        }

        // Fallback to event type-based context
        return getDefaultStrategyContext(eventType);
    }

    /**
     * Get default strategy context based on event type when strategy name is unknown
     */
    private java.util.Map<String, String> getDefaultStrategyContext(final String eventType) {
        if (eventType == null) {
            return java.util.Map.of(
                "name", "Generic Strategy",
                "profile", "Variable WR and R:R",
                "expected", "Clear setup with defined risk/reward"
            );
        }

        return switch (eventType.toLowerCase()) {
            case "reversal" -> java.util.Map.of(
                "name", "Reversal Setup",
                "profile", "45-55% WR, 1:3-1:5 R:R",
                "expected", "Clear reversal signal, volume confirmation, defined invalidation"
            );
            case "continuation" -> java.util.Map.of(
                "name", "Continuation Setup",
                "profile", "50-60% WR, 1:2-1:4 R:R",
                "expected", "Trend alignment, pullback to key level, momentum continuation"
            );
            case "breakout" -> java.util.Map.of(
                "name", "Breakout Setup",
                "profile", "30-40% WR, 1:5-1:10+ R:R",
                "expected", "Tight consolidation, volume surge, momentum confirmation"
            );
            case "sweep" -> java.util.Map.of(
                "name", "Liquidity Sweep",
                "profile", "45-55% WR, 1:3-1:5 R:R",
                "expected", "Equal highs/lows swept, displacement reversal, clear invalidation"
            );
            default -> java.util.Map.of(
                "name", "Generic Setup",
                "profile", "Variable WR and R:R",
                "expected", "Clear setup with defined risk/reward"
            );
        };
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
        log.info("Generating execution plan for {} - Direction: {} - Strategy: {}, Event: {}",
                context.getSymbol(), direction, webhook.getStrategy(), webhook.getEventType());

        try {
            // Build compact context with strategy event data and market context
            final var ctx = objectMapper.createObjectNode();

            // Strategy event data
            ctx.put("strategy", webhook.getStrategy());
            ctx.put("eventType", webhook.getEventType());
            ctx.put("direction", webhook.getDirection());
            ctx.put("tf", webhook.getTimeframe());
            ctx.put("session", webhook.getSession());

            // Context fields (if available)
            if (webhook.getSweptLevel() != null) {
                ctx.put("sweptLevel", webhook.getSweptLevel());
            }
            if (webhook.getCurrentPrice() != null) {
                ctx.put("currentPrice", webhook.getCurrentPrice());
            }
            if (webhook.getSuggestedStopLoss() != null) {
                ctx.put("suggestedStopLoss", webhook.getSuggestedStopLoss());
            }

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
            final var userMessage = "Plan execution for " + strategyContext.get("name") + " strategy:\n\n" +
                    "Target profile: " + strategyContext.get("profile") + "\n\n" +
                    "Generate plan: entry model, entry zone, stop logic (match strategy risk profile), " +
                    "targets (match expected R:R from profile), invalidation conditions.\n\n" +
                    "For Liquidity Sweeps: Stop beyond swept level, 3-5R targets\n" +
                    "For Candle 2 Closure: Tight stops below pattern, 2-3R targets\n" +
                    "For Breakout: Inside range stops, 5-10R+ targets\n" +
                    "For Reversal events: Stops beyond invalidation level\n" +
                    "For Continuation events: Stops below pullback low\n\n" +
                    contextJson + "\n\n" +
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

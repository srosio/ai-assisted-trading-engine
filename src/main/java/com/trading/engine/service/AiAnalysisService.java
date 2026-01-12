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
        log.info("Requesting AI analysis for {} - Pine Script Event: {}", context.getSymbol(), webhook.getEvent());

        try {
            // Build compact context with Pine Script event data
            final var compactContext = objectMapper.createObjectNode();

            // Pine Script event data (primary analysis focus)
            compactContext.put("event", webhook.getEvent());
            compactContext.put("tf", webhook.getTimeframe());
            compactContext.put("sweptHigh", webhook.getSweptHigh());
            compactContext.put("sweptLow", webhook.getSweptLow());
            compactContext.put("volSpike", webhook.getVolumeSpike());
            compactContext.put("displ", webhook.getDisplacementDetected());

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

            // Strategy-specific prompt based on documented Pine Script strategies
            final var strategyContext = getStrategyContext(webhook.getEvent());
            final var userMessage = "Analyze Pine Script " + strategyContext.get("name") + " strategy alert:\n\n" +
                    "Strategy profile: " + strategyContext.get("profile") + "\n" +
                    "Expected: " + strategyContext.get("expected") + "\n\n" +
                    "Assess setup quality based on swept levels, volume/displacement, and market context.\n" +
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
     * Get strategy-specific context based on Pine Script strategy type
     */
    private java.util.Map<String, String> getStrategyContext(final String event) {
        final var eventLower = event.toLowerCase();

        // High Win Rate Scalping (70-80% WR, 1:1-1:2 R:R)
        if (eventLower.contains("scalping") || eventLower.contains("bb_extreme") ||
            eventLower.contains("mean_reversion")) {
            return java.util.Map.of(
                "name", "High Win Rate Scalping",
                "profile", "70-80% WR, 1:1-1:2 R:R, mean reversion at extremes",
                "expected", "Tight BB squeeze, RSI extreme, clear support/resistance for reversal"
            );
        }

        // Medium Win Rate Swing (50-60% WR, 1:2-1:4 R:R)
        if (eventLower.contains("swing") || eventLower.contains("pullback") ||
            eventLower.contains("fib_retracement")) {
            return java.util.Map.of(
                "name", "Medium Win Rate Swing",
                "profile", "50-60% WR, 1:2-1:4 R:R, trend continuation",
                "expected", "Clear trend, pullback to key level (Fib/MA), continuation setup"
            );
        }

        // Low Win Rate Breakout (30-40% WR, 1:5-1:10+ R:R)
        if (eventLower.contains("breakout") || eventLower.contains("consolidation_break") ||
            eventLower.contains("range_break")) {
            return java.util.Map.of(
                "name", "Low Win Rate Breakout",
                "profile", "30-40% WR, 1:5-1:10+ R:R, explosive moves",
                "expected", "Tight consolidation, volume surge, momentum confirmation"
            );
        }

        // Adaptive Strategy (40-60% WR, 1:2-1:4 R:R)
        if (eventLower.contains("adaptive") || eventLower.contains("regime") ||
            eventLower.contains("multi_strategy")) {
            return java.util.Map.of(
                "name", "Adaptive Strategy",
                "profile", "40-60% WR, 1:2-1:4 R:R, regime-aware",
                "expected", "Clear regime identification, appropriate entry for market condition"
            );
        }

        // Liquidity Sweep (45-55% WR, 1:3-1:5 R:R) - Default/Original
        return java.util.Map.of(
            "name", "Liquidity Sweep",
            "profile", "45-55% WR, 1:3-1:5 R:R, liquidity grab reversal",
            "expected", "Equal highs/lows swept, displacement reversal, clear invalidation"
        );
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
        log.info("Generating execution plan for {} - Direction: {} - Event: {}",
                context.getSymbol(), direction, webhook.getEvent());

        try {
            // Build compact context with Pine Script event data and market context
            final var ctx = objectMapper.createObjectNode();

            // Pine Script event data
            ctx.put("event", webhook.getEvent());
            ctx.put("tf", webhook.getTimeframe());
            ctx.put("sweptHigh", webhook.getSweptHigh());
            ctx.put("sweptLow", webhook.getSweptLow());

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
            final var strategyContext = getStrategyContext(webhook.getEvent());
            final var userMessage = "Plan execution for Pine Script " + strategyContext.get("name") + " strategy:\n\n" +
                    "Target profile: " + strategyContext.get("profile") + "\n\n" +
                    "Generate plan: entry model, zone near swept levels, stop (match strategy risk), targets (match expected R:R), invalidations.\n\n" +
                    "For Scalping: Tight stops, quick 1-2R targets\n" +
                    "For Swing: Wider stops, 2-4R targets\n" +
                    "For Breakout: Inside range stops, 5-10R+ targets\n" +
                    "For Adaptive: Match regime (tight for range, wide for trend)\n" +
                    "For Liquidity: Beyond swept level, 3-5R targets\n\n" +
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
            final var strategyContext = getStrategyContext(webhook.getEvent());
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

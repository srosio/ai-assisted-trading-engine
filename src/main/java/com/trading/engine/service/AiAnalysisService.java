package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AiAnalysisService {

    private final ChatClient sonnetClient;
    private final ChatClient haikuClient;
    private final ClaudeConfig claudeConfig;
    private final StaticAnalysisService staticAnalysis;
    private final AiCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AiAnalysisService(
            ChatClient chatClient,
            @Qualifier("haikuChatClient") ChatClient haikuChatClient,
            ClaudeConfig claudeConfig,
            StaticAnalysisService staticAnalysis,
            AiCacheService cacheService) {
        this.sonnetClient = chatClient;
        this.haikuClient = haikuChatClient;
        this.claudeConfig = claudeConfig;
        this.staticAnalysis = staticAnalysis;
        this.cacheService = cacheService;
    }

    public CombinedAiAnalysis analyzeCombined(final MarketContext context,
                                                final TradingViewWebhook webhook,
                                                final IntradayContext intradayContext,
                                                final String direction) {
        log.info("Starting optimized AI analysis for {} - Strategy: {}, Confidence: {}",
                webhook.getSymbol(), webhook.getStrategy(), intradayContext.getConfidenceScore());

        try {
            if (claudeConfig.isEnableCaching()) {
                final var cached = cacheService.get(context, webhook, intradayContext);
                if (cached != null) {
                    return cached;
                }
            }

            if (claudeConfig.isEnableHaikuPrefilter()) {
                log.info("Pre-filtering with Haiku model");
                final var haikuQuality = performHaikuPrefilter(context, webhook, intradayContext);

                if ("C".equals(haikuQuality)) {
                    log.info("Haiku rejected setup (Quality C) - skipping Sonnet analysis");
                    final var rejected = createRejectedAnalysis(haikuQuality);

                    // Cache rejection to avoid re-analysis
                    if (claudeConfig.isEnableCaching()) {
                        cacheService.put(context, webhook, intradayContext, rejected);
                    }

                    return rejected;
                }

                log.info("Haiku approved setup (Quality {}) - proceeding with Sonnet analysis", haikuQuality);
            }

            final var analysis = performCombinedAnalysis(context, webhook, intradayContext, direction);

            if (claudeConfig.isEnableCaching()) {
                cacheService.put(context, webhook, intradayContext, analysis);
            }

            log.info("AI analysis complete - Quality: {}, Model: {}",
                    analysis.getSetupQuality(),
                    analysis.getExecutionModel() != null ? analysis.getExecutionModel() : "none");

            return analysis;

        } catch (final Exception e) {
            log.warn("AI unavailable, using static analysis: {}", e.getMessage());
            return convertToCombined(
                    staticAnalysis.generateStaticAssessment(context, intradayContext, webhook),
                    staticAnalysis.generateStaticExecutionPlan(
                            context, intradayContext, webhook, direction,
                            getStrategyName(webhook.getStrategy(), webhook.getEventType())
                    )
            );
        }
    }

    private String performHaikuPrefilter(final MarketContext context,
                                          final TradingViewWebhook webhook,
                                                final IntradayContext intradayContext) {
        try {
            final var compactContext = buildCompactContext(context, webhook, intradayContext);
            final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());

            final var userMessage = "Quick quality check for " + strategyContext.get("name") + " setup:\n" +
                    "Profile: " + strategyContext.get("profile") + "\n" +
                    "Expected: " + strategyContext.get("expected") + "\n\n" +
                    "Assess if this setup meets minimum criteria. Respond with ONLY one letter:\n" +
                    "A = Excellent (all criteria met)\n" +
                    "B = Good (most criteria met, minor concerns)\n" +
                    "C = Reject (missing key criteria)\n\n" +
                    compactContext;

            final var response = haikuClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();

            // Extract quality from response (should be just "A", "B", or "C")
            final var quality = response.trim().substring(0, 1).toUpperCase();

            if (List.of("A", "B", "C").contains(quality)) {
                log.info("Haiku pre-filter result: Quality {}", quality);
                return quality;
            }

            log.warn("Haiku returned invalid quality: {}, defaulting to B", response);
            return "B";  // Default to B (proceed with Sonnet analysis)

        } catch (final Exception e) {
            log.warn("Haiku pre-filter failed: {}, proceeding with Sonnet", e.getMessage());
            return "B";  // On error, proceed with Sonnet analysis
        }
    }

    private CombinedAiAnalysis performCombinedAnalysis(final MarketContext context,
                                                        final TradingViewWebhook webhook,
                                                        final IntradayContext intradayContext,
                                                        final String direction) throws Exception {
        final var compactContext = buildCompactContext(context, webhook, intradayContext);
        final var strategyContext = getStrategyContextByName(webhook.getStrategy(), webhook.getEventType());
        final var outputConverter = new BeanOutputConverter<>(CombinedAiAnalysis.class);

        final var userMessage = "Analyze " + strategyContext.get("name") + " setup and create execution plan:\n\n" +
                "PART 1 - SETUP ASSESSMENT:\n" +
                "Profile: " + strategyContext.get("profile") + "\n" +
                "Expected criteria: " + strategyContext.get("expected") + "\n" +
                "Assess setup quality (A/B/C), identify risk factors, and invalidation conditions.\n\n" +
                "PART 2 - EXECUTION PLAN (if quality is A or B):\n" +
                "Target profile: " + strategyContext.get("profile") + "\n" +
                "Generate: entry model, entry zone, stop logic, targets matching expected R:R, invalidation.\n" +
                "Guidelines:\n" +
                "- Liquidity Sweeps: Stop beyond swept level, 3-5R targets\n" +
                "- Candle 2 Closure: Tight stops below pattern, 2-3R targets\n" +
                "- Breakout: Inside range stops, 5-10R+ targets\n" +
                "If quality is C, leave execution fields empty.\n\n" +
                "Market Context:\n" + compactContext + "\n\n" +
                "Direction: " + direction + "\n\n" +
                outputConverter.getFormat();

        final var response = sonnetClient.prompt()
                .user(userMessage)
                .call()
                .content();

        final var combined = outputConverter.convert(response);
        validateCombinedAnalysis(combined);

        return combined;
    }

    private String buildCompactContext(final MarketContext context,
                                        final TradingViewWebhook webhook,
                                        final IntradayContext intradayContext) throws Exception {
        final var ctx = objectMapper.createObjectNode();

        // Strategy event data
        ctx.put("strategy", webhook.getStrategy());
        ctx.put("eventType", webhook.getEventType());
        ctx.put("direction", webhook.getDirection());
        ctx.put("tf", webhook.getTimeframe());
        ctx.put("session", webhook.getSession());

        // Optional context fields
        if (webhook.getSweptLevel() != null) ctx.put("sweptLevel", webhook.getSweptLevel());
        if (webhook.getHtfBias() != null) ctx.put("htfBias", webhook.getHtfBias());
        if (webhook.getDisplacement() != null) ctx.put("displacement", webhook.getDisplacement());
        if (webhook.getVolumeSpike() != null) ctx.put("volumeSpike", webhook.getVolumeSpike());
        if (webhook.getCurrentPrice() != null) ctx.put("currentPrice", webhook.getCurrentPrice());
        if (webhook.getSuggestedStopLoss() != null) ctx.put("suggestedStopLoss", webhook.getSuggestedStopLoss());

        // Market context
        ctx.put("sym", context.getSymbol());
        ctx.put("px", context.getCurrentPrice());
        ctx.put("htf", context.getHtfBias());
        ctx.put("sess", context.getSession());
        ctx.put("oiChg", context.getOiChangePercent());
        ctx.put("fund", context.getFundingRate());
        ctx.put("vol", context.getVolatility());

        // Intraday context
        ctx.put("t15", intradayContext.getTrendBias15m());
        ctx.put("t5", intradayContext.getTrendBias5m());
        ctx.put("oiPx", intradayContext.getOiPriceBehavior());
        ctx.put("volConf", intradayContext.getVolumeConfirmation());
        ctx.put("narrative", intradayContext.getSessionNarrative());
        ctx.put("conf", intradayContext.getConfidenceScore());

        return objectMapper.writeValueAsString(ctx);
    }

    private CombinedAiAnalysis createRejectedAnalysis(String quality) {
        return CombinedAiAnalysis.builder()
                .setupQuality(quality)
                .riskFactors(List.of("Setup does not meet minimum criteria"))
                .invalidation("Failed pre-screening")
                .summary("Setup rejected by AI pre-filter")
                .alignmentScore(30)
                .keyObservation("Pre-filter rejection - Sonnet analysis skipped")
                .build();
    }

    private CombinedAiAnalysis convertToCombined(AiAssessment assessment, ExecutionPlan executionPlan) {
        final var builder = CombinedAiAnalysis.builder()
                .setupQuality(assessment.getSetupQuality())
                .riskFactors(assessment.getRiskFactors())
                .invalidation(assessment.getInvalidation())
                .summary(assessment.getSummary())
                .alignmentScore(assessment.getAlignmentScore())
                .keyObservation(assessment.getKeyObservation());

        if (executionPlan != null) {
            builder.executionModel(executionPlan.getExecutionModel())
                    .entryZoneLow(executionPlan.getEntryZoneLow())
                    .entryZoneHigh(executionPlan.getEntryZoneHigh())
                    .stopLogic(executionPlan.getStopLogic())
                    .suggestedStopPrice(executionPlan.getSuggestedStopPrice())
                    .targets(executionPlan.getTargets())
                    .invalidationConditions(executionPlan.getInvalidationConditions())
                    .riskNotes(executionPlan.getRiskNotes())
                    .executionNotes(executionPlan.getExecutionNotes());
        }

        return builder.build();
    }

    private void validateCombinedAnalysis(final CombinedAiAnalysis analysis) {
        if (analysis.getSetupQuality() == null ||
            !List.of("A", "B", "C").contains(analysis.getSetupQuality())) {
            throw new IllegalStateException("Invalid setup quality: " + analysis.getSetupQuality());
        }

        if (analysis.getAlignmentScore() == null) {
            analysis.setAlignmentScore(getDefaultAlignmentScore(analysis.getSetupQuality()));
        } else if (analysis.getAlignmentScore() < 0 || analysis.getAlignmentScore() > 100) {
            analysis.setAlignmentScore(Math.max(0, Math.min(100, analysis.getAlignmentScore())));
        }
    }

    private java.util.Map<String, String> getStrategyContextByName(final String strategy, final String eventType) {
        if (strategy == null) {
            return getDefaultStrategyContext(eventType);
        }

        final var strategyLower = strategy.toLowerCase();

        if (strategyLower.contains("liquidity") || strategyLower.contains("sweep")) {
            return java.util.Map.of(
                "name", "Liquidity Sweeps",
                "profile", "45-55% WR, 1:3-1:5 R:R, liquidity grab reversal",
                "expected", "Equal highs/lows swept, displacement reversal, volume confirmation"
            );
        }

        if (strategyLower.contains("candle") || strategyLower.contains("closure") || strategyLower.contains("rsi")) {
            return java.util.Map.of(
                "name", "Candle 2 Closure with RSI",
                "profile", "60-70% WR, 1:2-1:3 R:R, two-candle reversal pattern",
                "expected", "RSI extreme (>70 or <30), two consecutive candles closing against trend"
            );
        }

        if (strategyLower.contains("breakout") || strategyLower.contains("consolidation")) {
            return java.util.Map.of(
                "name", "Breakout Strategy",
                "profile", "30-40% WR, 1:5-1:10+ R:R, explosive moves",
                "expected", "Tight consolidation, volume surge, momentum confirmation"
            );
        }

        return getDefaultStrategyContext(eventType);
    }

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

    private String getStrategyName(String strategy, String eventType) {
        return getStrategyContextByName(strategy, eventType).get("name");
    }

    private int getDefaultAlignmentScore(final String quality) {
        return switch (quality) {
            case "A" -> 80;
            case "B" -> 60;
            case "C" -> 40;
            default -> 50;
        };
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

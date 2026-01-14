package com.trading.engine.service;

import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Static analysis service that uses quantitative data from Binance and TradingView
 * when AI is unavailable. Provides deterministic analysis based on market metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaticAnalysisService {

    /**
     * Generate assessment using static calculations from Binance data
     */
    public AiAssessment generateStaticAssessment(final MarketContext context,
                                                   final IntradayContext intradayContext,
                                                   final TradingViewWebhook webhook) {
        log.info("Generating static assessment for {} using quantitative data", context.getSymbol());

        // Calculate setup quality based on quantitative metrics
        final var quality = calculateSetupQuality(context, intradayContext, webhook);
        final var alignmentScore = calculateAlignmentScore(context, intradayContext);
        final var riskFactors = identifyRiskFactors(context, intradayContext);
        final var invalidation = generateInvalidation(webhook, context);
        final var summary = generateSummary(quality, context, intradayContext);
        final var keyObservation = generateKeyObservation(context, intradayContext);

        return AiAssessment.builder()
                .setupQuality(quality)
                .alignmentScore(alignmentScore)
                .riskFactors(riskFactors)
                .invalidation(invalidation)
                .summary(summary)
                .keyObservation(keyObservation)
                .build();
    }

    /**
     * Generate execution plan using static calculations
     */
    public ExecutionPlan generateStaticExecutionPlan(final MarketContext context,
                                                       final IntradayContext intradayContext,
                                                       final TradingViewWebhook webhook,
                                                       final String direction,
                                                       final String strategyName) {
        log.info("Generating static execution plan for {} - Strategy: {}", context.getSymbol(), strategyName);

        final var executionModel = determineExecutionModel(intradayContext, strategyName);
        final var entryZone = calculateEntryZone(context, webhook, direction);
        final var stopPrice = calculateStopPrice(context, webhook, direction);
        final var targets = calculateTargets(context, stopPrice, direction, strategyName);
        final var invalidations = generateInvalidationConditions(webhook, context, direction);
        final var riskNotes = generateRiskNotes(context, intradayContext);

        return ExecutionPlan.builder()
                .executionModel(executionModel)
                .entryZoneLow(entryZone[0])
                .entryZoneHigh(entryZone[1])
                .stopLogic(generateStopLogic(webhook, direction))
                .suggestedStopPrice(stopPrice)
                .targets(targets)
                .invalidationConditions(invalidations)
                .riskNotes(riskNotes)
                .executionNotes("Static analysis - AI unavailable. Based on quantitative metrics.")
                .build();
    }

    private String calculateSetupQuality(final MarketContext context,
                                           final IntradayContext intradayContext,
                                           final TradingViewWebhook webhook) {
        var score = 0;

        // Volume confirmation (15 points)
        if (Boolean.TRUE.equals(webhook.getVolumeSpike())) {
            score += 15;
        }

        // Displacement detection (15 points)
        if (Boolean.TRUE.equals(webhook.getDisplacement())) {
            score += 15;
        }

        // HTF bias alignment (15 points)
        if (webhook.getHtfBias() != null && webhook.getDirection() != null) {
            final var htfBias = webhook.getHtfBias().toLowerCase();
            final var direction = webhook.getDirection().toLowerCase();
            if (htfBias.equals(direction)) {
                score += 15; // HTF and signal direction aligned
            } else {
                score -= 5; // Counter-HTF setup (more risk)
            }
        }

        // OI alignment (15 points)
        if (context.getOiChangePercent() != null && Math.abs(context.getOiChangePercent()) > 2.0) {
            score += 15;
        }

        // Intraday confidence (20 points)
        if (intradayContext != null && intradayContext.getConfidenceScore() != null) {
            score += (intradayContext.getConfidenceScore() / 5); // Scale to 20
        }

        // Volatility check (10 points)
        if ("normal".equals(context.getVolatility())) {
            score += 10;
        } else if ("high".equals(context.getVolatility())) {
            score += 5; // Half points for high volatility
        }

        // Strategy-specific bonus (10 points)
        if (webhook.getStrategy() != null) {
            final var strategy = webhook.getStrategy().toLowerCase();
            final var eventType = webhook.getEventType() != null ? webhook.getEventType().toLowerCase() : "";

            // Candle 2 Closure has high win rate
            if (strategy.contains("candle") && "reversal".equals(eventType)) {
                score += 10;
            }
            // Liquidity sweeps with swept level confirmation
            else if (strategy.contains("liquidity") && webhook.getSweptLevel() != null) {
                score += 10;
            }
            // Breakout with volume spike
            else if ("breakout".equals(eventType) && Boolean.TRUE.equals(webhook.getVolumeSpike())) {
                score += 10;
            }
        }

        // Classify based on score
        if (score >= 80) return "A";
        if (score >= 60) return "B";
        return "C";
    }

    private Integer calculateAlignmentScore(final MarketContext context,
                                             final IntradayContext intradayContext) {
        var score = 50; // Base score

        // OI change alignment (+/-15)
        if (context.getOiChangePercent() != null) {
            if (Math.abs(context.getOiChangePercent()) > 5.0) {
                score += 15; // Strong OI change
            } else if (Math.abs(context.getOiChangePercent()) > 2.0) {
                score += 10; // Moderate OI change
            }
        }

        // Funding rate check (+/-10)
        if (context.getFundingRate() != null) {
            final var fundingAbs = Math.abs(context.getFundingRate());
            if (fundingAbs < 0.0001) {
                score += 10; // Neutral funding
            } else if (fundingAbs > 0.001) {
                score -= 10; // Extreme funding (risk)
            }
        }

        // Volatility assessment (+/-10)
        if ("normal".equals(context.getVolatility())) {
            score += 10;
        } else if ("extreme".equals(context.getVolatility())) {
            score -= 15;
        }

        // Intraday confidence (+/-15)
        if (intradayContext != null && intradayContext.getConfidenceScore() != null) {
            final var conf = intradayContext.getConfidenceScore();
            if (conf >= 80) {
                score += 15;
            } else if (conf >= 60) {
                score += 10;
            } else if (conf < 40) {
                score -= 10;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    private List<String> identifyRiskFactors(final MarketContext context,
                                               final IntradayContext intradayContext) {
        final var risks = new ArrayList<String>();

        // Funding rate risks
        if (context.getFundingRate() != null) {
            final var fundingAbs = Math.abs(context.getFundingRate());
            if (fundingAbs > 0.001) {
                risks.add(String.format("Extreme funding rate: %.4f%% - high risk of unwind", context.getFundingRate() * 100));
            } else if (fundingAbs > 0.0005) {
                risks.add(String.format("Elevated funding rate: %.4f%% - monitor closely", context.getFundingRate() * 100));
            }
        }

        // Volatility risks
        if ("high".equals(context.getVolatility())) {
            risks.add("High volatility environment - wider stops required, reduce position size");
        } else if ("extreme".equals(context.getVolatility())) {
            risks.add("Extreme volatility - significant slippage risk, consider avoiding trade");
        }

        // OI risks
        if (context.getOiChangePercent() != null) {
            if (Math.abs(context.getOiChangePercent()) > 10.0) {
                risks.add(String.format("Extreme OI change: %.1f%% - potential manipulation or stop hunt", context.getOiChangePercent()));
            } else if (Math.abs(context.getOiChangePercent()) < 0.5) {
                risks.add("Very low OI change - lack of conviction, weak setup");
            }
        }

        // Low confidence warning
        if (intradayContext != null && intradayContext.getConfidenceScore() != null &&
            intradayContext.getConfidenceScore() < 50) {
            risks.add(String.format("Low intraday confidence: %d/100 - setup lacks confirmation",
                    intradayContext.getConfidenceScore()));
        }

        // Missing context data
        if (context.getVolumeSpike() != null && !context.getVolumeSpike()) {
            risks.add("No volume spike detected - breakout may be weak");
        }

        if (context.getDisplacementDetected() != null && !context.getDisplacementDetected()) {
            risks.add("No displacement detected - may lack momentum for follow-through");
        }

        if (risks.isEmpty()) {
            risks.add("No major quantitative risks identified - standard trade management applies");
        }

        return risks;
    }

    private String generateInvalidation(final TradingViewWebhook webhook, final MarketContext context) {
        // Map direction from bullish/bearish to LONG/SHORT
        final var direction = webhook.getDirection() != null &&
                webhook.getDirection().equalsIgnoreCase("bullish") ? "LONG" : "SHORT";

        if (webhook.getSweptLevel() != null) {
            if (direction.equals("SHORT")) {
                return String.format("Price moving back above swept level: %s", webhook.getSweptLevel());
            } else {
                return String.format("Price moving back below swept level: %s", webhook.getSweptLevel());
            }
        }

        if (webhook.getSuggestedStopLoss() != null) {
            return String.format("Stop loss hit at: %s", webhook.getSuggestedStopLoss());
        }

        return "Stop loss hit or setup structure breaks";
    }

    private String generateSummary(final String quality, final MarketContext context,
                                     final IntradayContext intradayContext) {
        final var oiChange = context.getOiChangePercent() != null ? context.getOiChangePercent() : 0.0;
        final var confidence = intradayContext != null && intradayContext.getConfidenceScore() != null ?
                intradayContext.getConfidenceScore() : 0;

        return String.format(
                "Static quantitative analysis (AI unavailable): Setup quality rated %s based on market metrics. " +
                "Open Interest: %.1f%%, Volatility: %s, Intraday Confidence: %d/100. " +
                "Analysis uses Binance derivatives data and TradingView price action. " +
                "Manual verification recommended before execution.",
                quality,
                oiChange,
                context.getVolatility(),
                confidence
        );
    }

    private String generateKeyObservation(final MarketContext context, final IntradayContext intradayContext) {
        if (intradayContext != null && intradayContext.getConfidenceScore() != null &&
            intradayContext.getConfidenceScore() >= 70) {
            return String.format("High confidence setup (%d/100) with %s OI behavior",
                    intradayContext.getConfidenceScore(),
                    intradayContext.getOiPriceBehavior());
        }

        if (context.getOiChangePercent() != null && Math.abs(context.getOiChangePercent()) > 5.0) {
            return String.format("Strong OI movement: %.1f%% - significant institutional activity",
                    context.getOiChangePercent());
        }

        return "Quantitative metrics suggest moderate setup quality";
    }

    private String determineExecutionModel(final IntradayContext intradayContext, final String strategyName) {
        final var strategy = strategyName != null ? strategyName.toLowerCase() : "";

        // Scalping strategies prefer limit orders for better price
        if (strategy.contains("scalp")) {
            return "limit";
        }

        // Breakout strategies prefer market orders for momentum capture
        if (strategy.contains("breakout")) {
            return "market";
        }

        // Candle closure patterns: limit orders at pattern completion
        if (strategy.contains("candle") || strategy.contains("closure")) {
            return "limit";
        }

        // Liquidity sweeps: can use market if strong displacement
        if (strategy.contains("liquidity") || strategy.contains("sweep")) {
            if (intradayContext != null && "confirmed".equals(intradayContext.getVolumeConfirmation())) {
                return "market"; // Strong sweep, take market
            }
            return "limit"; // Weak sweep, wait for better price
        }

        // Use volume confirmation to decide for unknown strategies
        if (intradayContext != null && "confirmed".equals(intradayContext.getVolumeConfirmation())) {
            return "market";
        }

        // Default to limit for better price execution
        return "limit";
    }

    private BigDecimal[] calculateEntryZone(final MarketContext context,
                                              final TradingViewWebhook webhook,
                                              final String direction) {
        final var currentPrice = context.getCurrentPrice();
        final var atr = context.getAtrValue() != null ? context.getAtrValue() : currentPrice.multiply(new BigDecimal("0.01"));

        // Entry zone is +/- 0.25 ATR from current price
        final var range = atr.multiply(new BigDecimal("0.25"));

        if (direction.equals("LONG")) {
            return new BigDecimal[]{
                    currentPrice.subtract(range).setScale(2, RoundingMode.HALF_UP),
                    currentPrice.add(range).setScale(2, RoundingMode.HALF_UP)
            };
        } else {
            return new BigDecimal[]{
                    currentPrice.subtract(range).setScale(2, RoundingMode.HALF_UP),
                    currentPrice.add(range).setScale(2, RoundingMode.HALF_UP)
            };
        }
    }

    private BigDecimal calculateStopPrice(final MarketContext context,
                                           final TradingViewWebhook webhook,
                                           final String direction) {
        final var currentPrice = context.getCurrentPrice();
        final var atr = context.getAtrValue() != null ? context.getAtrValue() : currentPrice.multiply(new BigDecimal("0.01"));

        // First priority: Use webhook suggested stop loss if available
        if (webhook.getSuggestedStopLoss() != null) {
            return webhook.getSuggestedStopLoss().setScale(2, RoundingMode.HALF_UP);
        }

        // Second priority: Use swept level with buffer
        if (webhook.getSweptLevel() != null) {
            final var buffer = atr.multiply(new BigDecimal("0.3"));
            if (direction.equals("LONG")) {
                // For longs, stop below swept level
                return webhook.getSweptLevel().subtract(buffer).setScale(2, RoundingMode.HALF_UP);
            } else {
                // For shorts, stop above swept level
                return webhook.getSweptLevel().add(buffer).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Fallback: Strategy-specific ATR multiplier
        BigDecimal atrMultiplier = new BigDecimal("1.5"); // Default

        if (webhook.getStrategy() != null) {
            final var strategy = webhook.getStrategy().toLowerCase();
            if (strategy.contains("candle") || strategy.contains("scalp")) {
                atrMultiplier = new BigDecimal("1.0"); // Tight stops for scalping/reversal patterns
            } else if (strategy.contains("breakout")) {
                atrMultiplier = new BigDecimal("2.0"); // Wider stops for breakouts
            } else if (strategy.contains("liquidity")) {
                atrMultiplier = new BigDecimal("1.5"); // Standard for liquidity sweeps
            }
        }

        // Apply ATR-based stop
        if (direction.equals("LONG")) {
            return currentPrice.subtract(atr.multiply(atrMultiplier)).setScale(2, RoundingMode.HALF_UP);
        } else {
            return currentPrice.add(atr.multiply(atrMultiplier)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private List<ExecutionPlan.Target> calculateTargets(final MarketContext context,
                                                          final BigDecimal stopPrice,
                                                          final String direction,
                                                          final String strategyName) {
        final var currentPrice = context.getCurrentPrice();
        final var risk = currentPrice.subtract(stopPrice).abs();

        final var targets = new ArrayList<ExecutionPlan.Target>();

        // Strategy-specific R:R ratios based on documented win rates
        if (strategyName.toLowerCase().contains("candle") || strategyName.toLowerCase().contains("closure")) {
            // Candle 2 Closure: 60-70% WR, 1:2-1:3 R:R
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "First target - pattern completion"));
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Extended target - structure level"));
        } else if (strategyName.toLowerCase().contains("liquidity") || strategyName.toLowerCase().contains("sweep")) {
            // Liquidity Sweeps: 45-55% WR, 1:3-1:5 R:R
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Previous structure / opposite sweep"));
            targets.add(createTarget(currentPrice, risk, 5.0, direction, "Major level / extended sweep"));
        } else if (strategyName.toLowerCase().contains("breakout")) {
            // Breakout: 30-40% WR, 1:5-1:10+ R:R
            targets.add(createTarget(currentPrice, risk, 5.0, direction, "Measured move"));
            targets.add(createTarget(currentPrice, risk, 10.0, direction, "Extended breakout target"));
        } else if (strategyName.toLowerCase().contains("scalp")) {
            // Scalping: Higher WR, lower R:R
            targets.add(createTarget(currentPrice, risk, 1.0, direction, "Quick profit"));
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "Extended scalp"));
        } else if (strategyName.toLowerCase().contains("swing")) {
            // Swing: 50-60% WR, 1:2-1:4 R:R
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "First resistance"));
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Key structure"));
            targets.add(createTarget(currentPrice, risk, 4.0, direction, "Extended swing"));
        } else {
            // Generic/Unknown strategy: Conservative 1:2 and 1:3
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "Conservative target"));
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Extended target"));
        }

        return targets;
    }

    private ExecutionPlan.Target createTarget(final BigDecimal currentPrice,
                                                final BigDecimal risk,
                                                final double rMultiple,
                                                final String direction,
                                                final String logic) {
        final var reward = risk.multiply(BigDecimal.valueOf(rMultiple));
        final var targetPrice = direction.equals("LONG") ?
                currentPrice.add(reward) : currentPrice.subtract(reward);

        return ExecutionPlan.Target.builder()
                .price(targetPrice.setScale(2, RoundingMode.HALF_UP))
                .rMultiple(rMultiple)
                .logic(logic)
                .build();
    }

    private String generateStopLogic(final TradingViewWebhook webhook, final String direction) {
        // First: Check if webhook provides suggested stop loss
        if (webhook.getSuggestedStopLoss() != null) {
            return "Using strategy-suggested stop loss from webhook";
        }

        // Second: Check if swept level exists
        if (webhook.getSweptLevel() != null) {
            if (direction.equals("LONG")) {
                return String.format("Below swept level (%.2f) + 0.3 ATR buffer", webhook.getSweptLevel());
            } else {
                return String.format("Above swept level (%.2f) + 0.3 ATR buffer", webhook.getSweptLevel());
            }
        }

        // Fallback: Strategy-specific ATR-based logic
        if (webhook.getStrategy() != null) {
            final var strategy = webhook.getStrategy().toLowerCase();
            if (strategy.contains("candle") || strategy.contains("scalp")) {
                return "1.0 ATR from entry - tight stop for reversal pattern";
            } else if (strategy.contains("breakout")) {
                return "2.0 ATR from entry - wider stop for volatility expansion";
            } else if (strategy.contains("liquidity")) {
                return "1.5 ATR from entry - standard stop for sweep strategy";
            }
        }

        return "1.5 ATR from entry - default static calculation";
    }

    private List<String> generateInvalidationConditions(final TradingViewWebhook webhook,
                                                          final MarketContext context,
                                                          final String direction) {
        final var conditions = new ArrayList<String>();

        // Primary invalidation: Stop loss
        if (webhook.getSuggestedStopLoss() != null) {
            conditions.add(String.format("Stop loss hit at %.2f", webhook.getSuggestedStopLoss()));
        } else {
            conditions.add("Stop loss hit");
        }

        // Swept level invalidation
        if (webhook.getSweptLevel() != null) {
            if (direction.equals("LONG")) {
                conditions.add(String.format("Price breaks below swept level: %.2f", webhook.getSweptLevel()));
            } else {
                conditions.add(String.format("Price breaks above swept level: %.2f", webhook.getSweptLevel()));
            }
        }

        // Structure invalidation
        if (direction.equals("LONG")) {
            conditions.add("Market structure break: Lower low below recent swing low");
        } else {
            conditions.add("Market structure break: Higher high above recent swing high");
        }

        // HTF bias change
        if (webhook.getHtfBias() != null) {
            conditions.add(String.format("HTF bias changes from %s - reassess trade validity", webhook.getHtfBias()));
        }

        // Strategy-specific invalidations
        if (webhook.getStrategy() != null) {
            final var strategy = webhook.getStrategy().toLowerCase();
            if (strategy.contains("candle") || strategy.contains("closure")) {
                conditions.add("Pattern failure: Next candle closes against setup");
            } else if (strategy.contains("liquidity") || strategy.contains("sweep")) {
                conditions.add("Re-sweep of same level - liquidity trap");
            } else if (strategy.contains("breakout")) {
                conditions.add("Failed breakout: Price returns inside range");
            }
        }

        // Risk management invalidations
        conditions.add("Funding rate exceeds 0.2% (extreme positioning risk)");
        conditions.add("Volatility spikes to extreme levels (risk of stop hunt)");

        return conditions;
    }

    private List<String> generateRiskNotes(final MarketContext context, final IntradayContext intradayContext) {
        final var notes = new ArrayList<String>();

        notes.add("AI unavailable - using static quantitative analysis");

        if ("high".equals(context.getVolatility()) || "extreme".equals(context.getVolatility())) {
            notes.add("High volatility environment - consider reducing position size");
        }

        if (context.getFundingRate() != null && Math.abs(context.getFundingRate()) > 0.0005) {
            notes.add("Elevated funding rate - monitor for position unwinds");
        }

        if (intradayContext != null && intradayContext.getConfidenceScore() != null &&
            intradayContext.getConfidenceScore() < 60) {
            notes.add("Below confidence threshold - trade with caution");
        }

        notes.add("Verify setup manually before execution");

        return notes;
    }
}

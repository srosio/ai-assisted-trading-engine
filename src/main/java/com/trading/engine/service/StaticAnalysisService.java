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

        // Volume confirmation (20 points)
        if (Boolean.TRUE.equals(webhook.getVolumeSpike())) {
            score += 20;
        }

        // Displacement detection (20 points)
        if (Boolean.TRUE.equals(webhook.getDisplacementDetected())) {
            score += 20;
        }

        // OI alignment (20 points)
        if (context.getOiChangePercent() != null && Math.abs(context.getOiChangePercent()) > 2.0) {
            score += 20;
        }

        // Intraday confidence (20 points)
        if (intradayContext != null && intradayContext.getConfidenceScore() != null) {
            score += (intradayContext.getConfidenceScore() / 5); // Scale to 20
        }

        // Volatility check (20 points)
        if ("normal".equals(context.getVolatility())) {
            score += 20;
        } else if ("high".equals(context.getVolatility())) {
            score += 10; // Half points for high volatility
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
                risks.add(String.format("Extreme funding rate: %.4f%%", context.getFundingRate() * 100));
            } else if (fundingAbs > 0.0005) {
                risks.add(String.format("Elevated funding rate: %.4f%%", context.getFundingRate() * 100));
            }
        }

        // Volatility risks
        if ("high".equals(context.getVolatility())) {
            risks.add("High volatility environment - wider stops required");
        } else if ("extreme".equals(context.getVolatility())) {
            risks.add("Extreme volatility - significant slippage risk");
        }

        // OI risks
        if (context.getOiChangePercent() != null && Math.abs(context.getOiChangePercent()) > 10.0) {
            risks.add(String.format("Extreme OI change: %.1f%% - potential manipulation", context.getOiChangePercent()));
        }

        // Low confidence warning
        if (intradayContext != null && intradayContext.getConfidenceScore() != null &&
            intradayContext.getConfidenceScore() < 50) {
            risks.add("Low intraday confidence score - setup lacks confirmation");
        }

        if (risks.isEmpty()) {
            risks.add("No major quantitative risks identified");
        }

        return risks;
    }

    private String generateInvalidation(final TradingViewWebhook webhook, final MarketContext context) {
        final var direction = webhook.getEvent().toLowerCase().contains("long") ? "LONG" : "SHORT";

        if (webhook.getSweptHigh() != null && direction.equals("SHORT")) {
            return String.format("Price moving back above swept high: %s", webhook.getSweptHigh());
        } else if (webhook.getSweptLow() != null && direction.equals("LONG")) {
            return String.format("Price moving back below swept low: %s", webhook.getSweptLow());
        }

        return "Stop loss hit or setup structure breaks";
    }

    private String generateSummary(final String quality, final MarketContext context,
                                     final IntradayContext intradayContext) {
        return String.format(
                "Static analysis (AI unavailable): Setup quality %s based on quantitative metrics. " +
                "OI: %.1f%%, Volatility: %s, Confidence: %d/100. " +
                "Decision based on market microstructure data from Binance and TradingView.",
                quality,
                context.getOiChangePercent() != null ? context.getOiChangePercent() : 0.0,
                context.getVolatility(),
                intradayContext != null && intradayContext.getConfidenceScore() != null ?
                        intradayContext.getConfidenceScore() : 0
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
        // Scalping strategies prefer limit orders
        if (strategyName.contains("Scalping")) {
            return "limit";
        }

        // Breakout strategies prefer market orders for momentum
        if (strategyName.contains("Breakout")) {
            return "market";
        }

        // Use volume confirmation to decide
        if (intradayContext != null && "confirmed".equals(intradayContext.getVolumeConfirmation())) {
            return "market";
        }

        // Default to limit for better price
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

        // Use swept levels if available
        if (direction.equals("LONG") && webhook.getSweptLow() != null) {
            return webhook.getSweptLow().subtract(atr.multiply(new BigDecimal("0.5"))).setScale(2, RoundingMode.HALF_UP);
        } else if (direction.equals("SHORT") && webhook.getSweptHigh() != null) {
            return webhook.getSweptHigh().add(atr.multiply(new BigDecimal("0.5"))).setScale(2, RoundingMode.HALF_UP);
        }

        // Fallback: 1.5 ATR stop
        if (direction.equals("LONG")) {
            return currentPrice.subtract(atr.multiply(new BigDecimal("1.5"))).setScale(2, RoundingMode.HALF_UP);
        } else {
            return currentPrice.add(atr.multiply(new BigDecimal("1.5"))).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private List<ExecutionPlan.Target> calculateTargets(final MarketContext context,
                                                          final BigDecimal stopPrice,
                                                          final String direction,
                                                          final String strategyName) {
        final var currentPrice = context.getCurrentPrice();
        final var risk = currentPrice.subtract(stopPrice).abs();

        final var targets = new ArrayList<ExecutionPlan.Target>();

        // Strategy-specific R:R ratios
        if (strategyName.contains("Scalping")) {
            // 1R and 2R for scalping
            targets.add(createTarget(currentPrice, risk, 1.0, direction, "Quick profit - BB middle"));
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "Extended target - BB opposite"));
        } else if (strategyName.contains("Breakout")) {
            // 5R and 10R for breakouts
            targets.add(createTarget(currentPrice, risk, 5.0, direction, "Measured move"));
            targets.add(createTarget(currentPrice, risk, 10.0, direction, "Extended breakout"));
        } else if (strategyName.contains("Swing")) {
            // 2R, 3R, 4R for swing
            targets.add(createTarget(currentPrice, risk, 2.0, direction, "First resistance"));
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Key level"));
            targets.add(createTarget(currentPrice, risk, 4.0, direction, "Extended swing"));
        } else {
            // Default: Liquidity Sweep 3R and 5R
            targets.add(createTarget(currentPrice, risk, 3.0, direction, "Previous structure"));
            targets.add(createTarget(currentPrice, risk, 5.0, direction, "Major level"));
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
        if (direction.equals("LONG") && webhook.getSweptLow() != null) {
            return "Below swept low + 0.5 ATR buffer";
        } else if (direction.equals("SHORT") && webhook.getSweptHigh() != null) {
            return "Above swept high + 0.5 ATR buffer";
        }
        return "1.5 ATR from entry - static calculation";
    }

    private List<String> generateInvalidationConditions(final TradingViewWebhook webhook,
                                                          final MarketContext context,
                                                          final String direction) {
        final var conditions = new ArrayList<String>();

        conditions.add("Stop loss hit");

        if (direction.equals("LONG") && webhook.getSweptLow() != null) {
            conditions.add("Price breaks below swept low: " + webhook.getSweptLow());
        } else if (direction.equals("SHORT") && webhook.getSweptHigh() != null) {
            conditions.add("Price breaks above swept high: " + webhook.getSweptHigh());
        }

        conditions.add("Market structure break (higher low for short, lower high for long)");
        conditions.add("Funding rate exceeds 0.2% (extreme positioning)");

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

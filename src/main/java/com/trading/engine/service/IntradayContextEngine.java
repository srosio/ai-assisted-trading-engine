package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Intraday Context Engine - Deterministic Market Analysis
 *
 * Analyzes market microstructure and produces:
 * - Trend bias per timeframe
 * - OI + price behavior classification
 * - Volume confirmation/divergence
 * - Session narrative
 * - Confidence score
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntradayContextEngine {

    private final MarketDataService marketData;

    /**
     * Build comprehensive intraday context with confidence threshold
     */
    public IntradayContext buildContext(final String symbol, final MarketContext marketContext) {
        log.info("Building intraday context for {}", symbol);

        // Fetch additional market microstructure data
        final var fundingDelta = marketData.getFundingRateDelta(symbol);
        final var takerRatio = marketData.getTakerBuySellRatio(symbol);
        final var orderBookImbalance = marketData.getOrderBookImbalance(symbol);
        final var spotPerpRatio = marketData.getSpotVsPerpVolumeRatio(symbol);
        final var liquidations = marketData.getRecentLiquidations(symbol);

        // Determine trend bias per timeframe (using price action + HTF bias)
        final var bias15m = determineTrendBias(marketContext, "15m");
        final var bias5m = determineTrendBias(marketContext, "5m");
        final var bias1m = determineTrendBias(marketContext, "1m");

        // Classify OI + price behavior
        final var oiPriceBehavior = classifyOIPriceBehavior(
                marketContext.getOiChangePercent(),
                marketContext.getCurrentPrice(),
                marketContext.getPreviousDayHigh(),
                marketContext.getPreviousDayLow()
        );

        // Analyze volume confirmation
        final var volumeConfirmation = analyzeVolumeConfirmation(
                marketContext.getVolumeSpike(),
                marketContext.getDisplacementDetected(),
                takerRatio
        );

        // Determine session narrative
        final var sessionNarrative = determineSessionNarrative(
                marketContext.getSession(),
                marketContext.getVolatility(),
                oiPriceBehavior
        );

        // Calculate confidence score
        final var confidence = calculateConfidenceScore(
                bias15m,
                bias5m,
                oiPriceBehavior,
                volumeConfirmation,
                fundingDelta,
                orderBookImbalance
        );

        // Generate context summary
        final var summary = generateContextSummary(
                oiPriceBehavior,
                sessionNarrative,
                volumeConfirmation,
                confidence
        );

        return IntradayContext.builder()
                .trendBias15m(bias15m)
                .trendBias5m(bias5m)
                .trendBias1m(bias1m)
                .oiPriceBehavior(oiPriceBehavior)
                .volumeConfirmation(volumeConfirmation)
                .sessionNarrative(sessionNarrative)
                .confidenceScore(confidence)
                .fundingRateDelta(fundingDelta)
                .takerBuySellRatio(takerRatio)
                .orderBookImbalance(orderBookImbalance)
                .spotVsPerpVolume(spotPerpRatio)
                .recentLiquidations(liquidations)
                .contextSummary(summary)
                .build();
    }

    /**
     * Determine trend bias for a given timeframe
     */
    private String determineTrendBias(final MarketContext context, final String timeframe) {
        // Use HTF bias as primary indicator
        final var htfBias = context.getHtfBias();

        // Check price location relative to day's range
        final var price = context.getCurrentPrice();
        final var dayHigh = context.getPreviousDayHigh();
        final var dayLow = context.getPreviousDayLow();

        if (price == null || dayHigh == null || dayLow == null) {
            return "neutral";
        }

        final var range = dayHigh.subtract(dayLow);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return "neutral";
        }

        final var pricePosition = price.subtract(dayLow).divide(range, 2, BigDecimal.ROUND_HALF_UP);

        // If in upper 30% and HTF bullish -> bullish
        if (pricePosition.compareTo(new BigDecimal("0.7")) > 0 && "bullish".equals(htfBias)) {
            return "bullish";
        }

        // If in lower 30% and HTF bearish -> bearish
        if (pricePosition.compareTo(new BigDecimal("0.3")) < 0 && "bearish".equals(htfBias)) {
            return "bearish";
        }

        // Otherwise neutral or consolidating
        return "neutral";
    }

    /**
     * Classify OI + Price behavior
     * - Long buildup: OI increasing + price rising
     * - Short buildup: OI increasing + price falling
     * - Long squeeze: OI decreasing + price falling
     * - Short squeeze: OI decreasing + price rising
     */
    private String classifyOIPriceBehavior(final Double oiChange,
                                            final BigDecimal currentPrice,
                                            final BigDecimal prevHigh,
                                            final BigDecimal prevLow) {
        if (oiChange == null || currentPrice == null || prevHigh == null || prevLow == null) {
            return "neutral";
        }

        // Determine if price is rising or falling relative to range
        final var midpoint = prevHigh.add(prevLow).divide(new BigDecimal("2"), 2, BigDecimal.ROUND_HALF_UP);
        final var priceRising = currentPrice.compareTo(midpoint) > 0;

        if (oiChange > 2.0 && priceRising) {
            return "long_buildup";
        } else if (oiChange > 2.0 && !priceRising) {
            return "short_buildup";
        } else if (oiChange < -2.0 && !priceRising) {
            return "long_squeeze";
        } else if (oiChange < -2.0 && priceRising) {
            return "short_squeeze";
        }

        return "neutral";
    }

    /**
     * Analyze volume confirmation vs divergence
     */
    private String analyzeVolumeConfirmation(final Boolean volumeSpike,
                                              final Boolean displacement,
                                              final Double takerRatio) {
        if (volumeSpike != null && volumeSpike && displacement != null && displacement) {
            // Volume spike + displacement = strong confirmation
            return "confirmed";
        }

        if (takerRatio != null) {
            // Check if taker flow aligns with price action
            if (Math.abs(takerRatio - 1.0) < 0.1) {
                return "neutral";
            } else if (takerRatio > 1.1 || takerRatio < 0.9) {
                return "confirmed";
            }
        }

        // No strong volume confirmation
        return "divergence";
    }

    /**
     * Determine session narrative based on market behavior
     */
    private String determineSessionNarrative(final String session,
                                              final String volatility,
                                              final String oiPriceBehavior) {
        // Range expansion during London/NY with buildup
        if (("LONDON".equals(session) || "NY".equals(session)) &&
                "expanding".equals(volatility) &&
                (oiPriceBehavior.contains("buildup"))) {
            return "range_expansion";
        }

        // Mean reversion during quiet sessions or contracting volatility
        if ("ASIA".equals(session) || "contracting".equals(volatility)) {
            return "mean_reversion";
        }

        // Trend continuation during squeezes
        if (oiPriceBehavior.contains("squeeze")) {
            return "trend_continuation";
        }

        return "range_expansion"; // Default
    }

    /**
     * Calculate confidence score (0-100)
     */
    private Integer calculateConfidenceScore(final String bias15m,
                                              final String bias5m,
                                              final String oiPriceBehavior,
                                              final String volumeConfirmation,
                                              final Double fundingDelta,
                                              final Double orderBookImbalance) {
        var score = 50; // Base score

        // Trend alignment across timeframes (+20)
        if (bias15m.equals(bias5m) && !bias15m.equals("neutral")) {
            score += 20;
        }

        // Clear OI+price behavior (+15)
        if (!oiPriceBehavior.equals("neutral")) {
            score += 15;
        }

        // Volume confirmation (+15)
        if ("confirmed".equals(volumeConfirmation)) {
            score += 15;
        }

        // Order book showing directional bias (+10)
        if (orderBookImbalance != null && Math.abs(orderBookImbalance - 1.0) > 0.2) {
            score += 10;
        }

        // Funding rate not extreme (-10 if extreme)
        if (fundingDelta != null && Math.abs(fundingDelta) > 0.0005) {
            score -= 10;
        }

        // Penalize divergence (-20)
        if ("divergence".equals(volumeConfirmation)) {
            score -= 20;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Generate human-readable context summary
     */
    private String generateContextSummary(final String oiPriceBehavior,
                                           final String sessionNarrative,
                                           final String volumeConfirmation,
                                           final Integer confidence) {
        return String.format("%s setup with %s pattern. Volume: %s. Confidence: %d/100",
                oiPriceBehavior.replace("_", " "),
                sessionNarrative.replace("_", " "),
                volumeConfirmation,
                confidence);
    }
}

package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntradayContextEngine {

    private final MarketDataService marketData;

    public IntradayContext buildContext(final String symbol, final MarketContext marketContext) {
        log.info("Building intraday context for {}", symbol);

        final var fundingDelta = marketData.getFundingRateDelta(symbol);
        final var takerRatio = marketData.getTakerBuySellRatio(symbol);
        final var orderBookImbalance = marketData.getOrderBookImbalance(symbol);
        final var spotPerpRatio = marketData.getSpotVsPerpVolumeRatio(symbol);
        final var liquidations = marketData.getRecentLiquidations(symbol);

        final var bias15m = determineTrendBias(marketContext, "15m");
        final var bias5m = determineTrendBias(marketContext, "5m");
        final var bias1m = determineTrendBias(marketContext, "1m");

        final var oiPriceBehavior = classifyOIPriceBehavior(
                marketContext.getOiChangePercent(),
                marketContext.getCurrentPrice(),
                marketContext.getPreviousDayHigh(),
                marketContext.getPreviousDayLow()
        );

        final var volumeConfirmation = analyzeVolumeConfirmation(
                marketContext.getVolumeSpike(),
                marketContext.getDisplacementDetected(),
                takerRatio
        );

        final var sessionNarrative = determineSessionNarrative(
                marketContext.getSession(),
                marketContext.getVolatility(),
                oiPriceBehavior
        );

        final var confidence = calculateConfidenceScore(
                bias15m,
                bias5m,
                oiPriceBehavior,
                volumeConfirmation,
                fundingDelta,
                orderBookImbalance
        );

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

    private String determineTrendBias(final MarketContext context, final String timeframe) {
        final var htfBias = context.getHtfBias();

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

        final var pricePosition = price.subtract(dayLow).divide(range, 2, RoundingMode.HALF_UP);

        if (pricePosition.compareTo(new BigDecimal("0.7")) > 0 && "bullish".equals(htfBias)) {
            return "bullish";
        }

        if (pricePosition.compareTo(new BigDecimal("0.3")) < 0 && "bearish".equals(htfBias)) {
            return "bearish";
        }

        return "neutral";
    }

    private String classifyOIPriceBehavior(final Double oiChange,
                                            final BigDecimal currentPrice,
                                            final BigDecimal prevHigh,
                                            final BigDecimal prevLow) {
        if (oiChange == null || currentPrice == null || prevHigh == null || prevLow == null) {
            return "neutral";
        }

        final var midpoint = prevHigh.add(prevLow).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
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

    private String analyzeVolumeConfirmation(final Boolean volumeSpike,
                                              final Boolean displacement,
                                              final Double takerRatio) {
        if (volumeSpike != null && volumeSpike && displacement != null && displacement) {
            return "confirmed";
        }

        if (takerRatio != null) {
            if (Math.abs(takerRatio - 1.0) < 0.1) {
                return "neutral";
            } else if (takerRatio > 1.1 || takerRatio < 0.9) {
                return "confirmed";
            }
        }

        return "divergence";
    }

    private String determineSessionNarrative(final String session,
                                              final String volatility,
                                              final String oiPriceBehavior) {
        if (("LONDON".equals(session) || "NY".equals(session)) &&
                "expanding".equals(volatility) &&
                (oiPriceBehavior.contains("buildup"))) {
            return "range_expansion";
        }

        if ("ASIA".equals(session) || "contracting".equals(volatility)) {
            return "mean_reversion";
        }

        if (oiPriceBehavior.contains("squeeze")) {
            return "trend_continuation";
        }

        return "range_expansion";
    }

    private Integer calculateConfidenceScore(final String bias15m,
                                              final String bias5m,
                                              final String oiPriceBehavior,
                                              final String volumeConfirmation,
                                              final Double fundingDelta,
                                              final Double orderBookImbalance) {
        var score = 50;

        if (bias15m.equals(bias5m) && !bias15m.equals("neutral")) {
            score += 20;
        }

        if (!oiPriceBehavior.equals("neutral")) {
            score += 15;
        }

        if ("confirmed".equals(volumeConfirmation)) {
            score += 15;
        }

        if (orderBookImbalance != null && Math.abs(orderBookImbalance - 1.0) > 0.2) {
            score += 10;
        }

        if (fundingDelta != null && Math.abs(fundingDelta) > 0.0005) {
            score -= 10;
        }

        if ("divergence".equals(volumeConfirmation)) {
            score -= 20;
        }

        return Math.max(0, Math.min(100, score));
    }

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

package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Intraday Context - Deterministic market analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayContext {

    // Trend bias per timeframe
    private String trendBias15m;  // bullish, bearish, neutral
    private String trendBias5m;   // bullish, bearish, neutral
    private String trendBias1m;   // bullish, bearish, neutral

    // OI + Price behavior classification
    private String oiPriceBehavior; // long_buildup, short_buildup, long_squeeze, short_squeeze

    // Volume analysis
    private String volumeConfirmation; // confirmed, divergence, neutral

    // Session narrative
    private String sessionNarrative; // range_expansion, mean_reversion, trend_continuation

    // Confidence score (0-100)
    private Integer confidenceScore;

    // Market microstructure
    private Double fundingRateDelta;
    private Double takerBuySellRatio;
    private Double orderBookImbalance;
    private Double spotVsPerpVolume;
    private Double recentLiquidations;

    // Context summary
    private String contextSummary;
}

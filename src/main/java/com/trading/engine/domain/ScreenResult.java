package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of technical pre-screening for a symbol.
 * Determines whether a symbol is "interesting" enough for AI analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenResult {

    private String symbol;
    private boolean interesting;         // Pass pre-screening?
    private int interestScore;           // 0-100

    // Market snapshot
    private BigDecimal currentPrice;
    private BigDecimal previousDayHigh;
    private BigDecimal previousDayLow;
    private Double oiChangePercent;
    private Double fundingRate;
    private String volatilityState;
    private BigDecimal atr;

    // Screening signals found
    private List<String> signals;        // e.g. "RSI extreme (22)", "Near PDH (0.3%)", "OI surge (+5.2%)"

    // Key levels
    private BigDecimal nearestSupport;
    private BigDecimal nearestResistance;
    private Double distanceToKeyLevel;   // % distance to nearest key level

    // Volume context
    private Double volumeRatio;          // Current vs average volume
    private Double takerRatio;

    // Price action
    private String priceLocation;        // above_range, upper_third, middle, lower_third, below_range
    private String trendBias;            // bullish, bearish, neutral
}

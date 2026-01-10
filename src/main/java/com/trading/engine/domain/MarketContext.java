package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Structured, factual snapshot of market conditions.
 * Contains ONLY objective data, no opinions or predictions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketContext {

    private String symbol;

    @JsonProperty("htf_bias")
    private String htfBias; // bullish, bearish, neutral

    private String location; // e.g., "below_previous_day_high"

    private String session; // London, NY, Asia

    @JsonProperty("oi_change_percent")
    private Double oiChangePercent; // Open Interest change

    @JsonProperty("funding_rate")
    private Double fundingRate;

    @JsonProperty("liquidity_event")
    private String liquidityEvent; // e.g., "equal_lows_swept"

    private String volatility; // expanding, contracting, stable

    @JsonProperty("current_price")
    private BigDecimal currentPrice;

    @JsonProperty("previous_day_high")
    private BigDecimal previousDayHigh;

    @JsonProperty("previous_day_low")
    private BigDecimal previousDayLow;

    @JsonProperty("volume_spike")
    private Boolean volumeSpike;

    @JsonProperty("displacement_detected")
    private Boolean displacementDetected;

    @JsonProperty("atr_value")
    private BigDecimal atrValue; // Average True Range for context

    @JsonProperty("nearest_resistance")
    private BigDecimal nearestResistance;

    @JsonProperty("nearest_support")
    private BigDecimal nearestSupport;
}

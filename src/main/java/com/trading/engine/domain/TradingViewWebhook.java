package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Universal TradingView webhook model with nested price/context structure.
 * Supports multiple strategies with standardized event types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingViewWebhook {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Timeframe is required")
    private String timeframe;

    @NotBlank(message = "Strategy is required")
    private String strategy;

    @NotBlank(message = "Event type is required")
    @JsonProperty("eventType")
    @Pattern(regexp = "reversal|continuation|breakout|sweep",
             message = "Event type must be: reversal, continuation, breakout, or sweep")
    private String eventType;

    @NotBlank(message = "Direction is required")
    @Pattern(regexp = "bullish|bearish",
             message = "Direction must be: bullish or bearish")
    private String direction;

    @NotBlank(message = "Session is required")
    private String session;

    @Valid
    @NotNull(message = "Price information is required")
    private PriceInfo price;

    @Valid
    private ContextInfo context;

    private Instant timestamp;

    /**
     * Price information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceInfo {

        @NotNull(message = "Close price is required")
        private BigDecimal close;

        @JsonProperty("stopLoss")
        private BigDecimal stopLoss;
    }

    /**
     * Context information about the market condition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextInfo {

        @JsonProperty("sweptLevel")
        private BigDecimal sweptLevel;

        @JsonProperty("htfBias")
        private String htfBias;

        private Boolean displacement;

        @JsonProperty("volumeSpike")
        private Boolean volumeSpike;

        @JsonProperty("previousDayHigh")
        private BigDecimal previousDayHigh;

        @JsonProperty("previousDayLow")
        private BigDecimal previousDayLow;

        @JsonProperty("rsiValue")
        private Double rsiValue;

        @JsonProperty("atrValue")
        private BigDecimal atrValue;

        @JsonProperty("candleMetrics")
        private CandleMetrics candleMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandleMetrics {
        @JsonProperty("wickPercent")
        private Double wickPercent;

        @JsonProperty("bodyPercent")
        private Double bodyPercent;

        private BigDecimal range;

        @JsonProperty("bodySize")
        private BigDecimal bodySize;
    }

    // === Convenience Getters ===

    public BigDecimal getCurrentPrice() {
        return price != null ? price.getClose() : null;
    }

    public BigDecimal getSuggestedStopLoss() {
        return price != null ? price.getStopLoss() : null;
    }

    public BigDecimal getSweptLevel() {
        return context != null ? context.getSweptLevel() : null;
    }

    public String getHtfBias() {
        return context != null ? context.getHtfBias() : null;
    }

    public Boolean getDisplacement() {
        return context != null ? context.getDisplacement() : null;
    }

    public Boolean getVolumeSpike() {
        return context != null ? context.getVolumeSpike() : null;
    }

    public BigDecimal getPreviousDayHigh() {
        return context != null ? context.getPreviousDayHigh() : null;
    }

    public BigDecimal getPreviousDayLow() {
        return context != null ? context.getPreviousDayLow() : null;
    }

    public Double getRsiValue() {
        return context != null ? context.getRsiValue() : null;
    }

    public BigDecimal getAtrValue() {
        return context != null ? context.getAtrValue() : null;
    }

    public CandleMetrics getCandleMetrics() {
        return context != null ? context.getCandleMetrics() : null;
    }

    public void ensureTimestamp() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}

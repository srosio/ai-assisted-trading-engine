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
    @JsonProperty("event_type")
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
        @JsonProperty("close")
        private BigDecimal close;

        @JsonProperty("stop_loss")
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

        @JsonProperty("swept_level")
        private BigDecimal sweptLevel;

        @JsonProperty("htf_bias")
        private String htfBias;

        @JsonProperty("displacement")
        private Boolean displacement;

        @JsonProperty("volume_spike")
        private Boolean volumeSpike;

        @JsonProperty("previous_day_high")
        private BigDecimal previousDayHigh;

        @JsonProperty("previous_day_low")
        private BigDecimal previousDayLow;
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

    public void ensureTimestamp() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}

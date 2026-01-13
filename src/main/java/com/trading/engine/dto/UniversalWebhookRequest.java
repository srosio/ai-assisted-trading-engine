package com.trading.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Universal webhook request schema for all TradingView strategies.
 * Supports multiple strategies with standardized event types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversalWebhookRequest {

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
    @NotNull(message = "Context information is required")
    private ContextInfo context;

    private Instant timestamp;

    /**
     * Price information including current price and optional stop loss
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceInfo {

        @NotNull(message = "Close price is required")
        private Double close;

        @JsonProperty("stop_loss")
        private Double stopLoss;
    }

    /**
     * Contextual information about the market condition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextInfo {

        @JsonProperty("swept_level")
        private Double sweptLevel;

        @JsonProperty("htf_bias")
        private String htfBias;

        private Boolean displacement;

        @JsonProperty("volume_spike")
        private Boolean volumeSpike;

        @JsonProperty("previous_day_high")
        private Double previousDayHigh;

        @JsonProperty("previous_day_low")
        private Double previousDayLow;
    }
}

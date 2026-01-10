package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Incoming webhook payload from TradingView.
 * Represents objective market events detected by Pine Script.
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

    @NotBlank(message = "Event is required")
    private String event;

    @NotBlank(message = "Session is required")
    private String session;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    @JsonProperty("previous_day_high")
    private BigDecimal previousDayHigh;

    @JsonProperty("previous_day_low")
    private BigDecimal previousDayLow;

    @JsonProperty("volume_spike")
    private Boolean volumeSpike;

    @JsonProperty("displacement_detected")
    private Boolean displacementDetected;

    @JsonProperty("swept_high")
    private BigDecimal sweptHigh;

    @JsonProperty("swept_low")
    private BigDecimal sweptLow;

    @JsonProperty("htf_bias")
    private String htfBias;
}

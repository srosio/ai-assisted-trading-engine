package com.trading.engine.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    private BigDecimal previousDayHigh;

    private BigDecimal previousDayLow;

    private Boolean volumeSpike;

    private Boolean displacementDetected;

    private BigDecimal sweptHigh;

    private BigDecimal sweptLow;

    private String htfBias;
}

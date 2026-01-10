package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketContext {

    private String symbol;

    private String htfBias;

    private String location;

    private String session;

    private Double oiChangePercent;

    private Double fundingRate;

    private String liquidityEvent;

    private String volatility;

    private BigDecimal currentPrice;

    private BigDecimal previousDayHigh;

    private BigDecimal previousDayLow;

    private Boolean volumeSpike;

    private Boolean displacementDetected;

    private BigDecimal atrValue;

    private BigDecimal nearestResistance;

    private BigDecimal nearestSupport;
}

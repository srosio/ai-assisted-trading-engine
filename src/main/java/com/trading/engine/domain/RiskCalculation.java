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
public class RiskCalculation {

    private BigDecimal accountBalance;

    private BigDecimal riskPercentage; // 0.5% - 1%

    private BigDecimal riskAmount; // Dollar amount at risk

    private BigDecimal entryPrice;

    private BigDecimal stopLoss;

    private BigDecimal takeProfit;

    private BigDecimal positionSize; // Quantity to trade

    private BigDecimal riskRewardRatio;

    private Integer tradesUsedToday;

    private Integer maxTradesPerDay; // 2

    private BigDecimal dailyLoss; // Current daily loss

    private BigDecimal maxDailyLoss; // -2R limit

    private boolean withinRiskLimits;

    private String limitViolation; // Description if limits exceeded
}

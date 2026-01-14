package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {

    private String signalId;

    private String symbol;

    private String direction; // LONG or SHORT

    private String strategy; // e.g., "Liquidity Sweeps", "Candle 2 Closure"

    private String eventType; // reversal, continuation, breakout, sweep

    private String event; // legacy field for backward compatibility in logs

    private MarketContext marketContext;

    private IntradayContext intradayContext;

    private AiAssessment aiAssessment;

    private ExecutionPlan executionPlan;

    private RuleResult ruleResult;

    private ExecutionChecklist executionChecklist;

    private LocalDateTime timestamp;

    private String status; // VALID, INVALID, EXPIRED

    private String action; // Human executes per execution plan
}

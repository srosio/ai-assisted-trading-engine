package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Complete trade signal after all validations.
 * This is NOT an order - it's information for the human trader.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {

    private String signalId;

    private String symbol;

    private String direction; // LONG or SHORT

    private String event; // liquidity_sweep_long, session_break, etc.

    private MarketContext marketContext;

    private AiAssessment aiAssessment;

    private RuleResult ruleResult;

    private RiskCalculation riskCalculation;

    private LocalDateTime timestamp;

    private String status; // VALID, INVALID, EXPIRED

    private String action; // "Monitor per trading plan", "Setup invalid", etc.
}

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

    private String event; // liquidity_sweep_long, session_break, etc.

    private MarketContext marketContext;

    private AiAssessment aiAssessment;

    private RuleResult ruleResult;

    private LocalDateTime timestamp;

    private String status; // VALID, INVALID, EXPIRED

    private String action; // Human decides entry and risk management
}

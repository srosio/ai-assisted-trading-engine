package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pre-execution checklist for trade readiness
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionChecklist {

    // Spread check
    private Boolean spreadOk;
    private Double currentSpreadBps; // Basis points

    // Funding rate check
    private Boolean fundingAcceptable;
    private Double currentFundingRate;
    private String fundingWarning;

    // Volatility check
    private Boolean volatilityWithinBounds;
    private String volatilityState;

    // Liquidity check
    private Boolean liquidityAdequate;
    private Double orderBookImbalance;

    // Overall readiness
    private Boolean readyForExecution;
    private List<String> warnings;
    private List<String> blockers;

    // Summary
    private String checklistSummary;
}

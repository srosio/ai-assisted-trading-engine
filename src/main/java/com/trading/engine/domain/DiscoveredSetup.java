package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI-discovered trading setup from autonomous market scanning.
 * The AI analyzes raw market data and identifies potential setups.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredSetup {

    // Setup identification
    private String direction;          // LONG or SHORT
    private String strategy;           // LIQUIDITY_SWEEP, CANDLE_2_CLOSURE, BREAKOUT_RETEST, REVERSAL, CONTINUATION
    private String eventType;          // reversal, continuation, breakout, sweep
    private String setupQuality;       // A, B, C

    // Setup reasoning
    private String setupDescription;   // What the AI sees
    private String triggerCondition;   // What triggered the setup identification
    private List<String> confluenceFactors;  // Supporting factors

    // Risk factors
    private List<String> riskFactors;
    private String invalidation;

    // Execution guidance
    private BigDecimal suggestedEntry;
    private BigDecimal suggestedStop;
    private List<ExecutionPlan.Target> targets;
    private String executionModel;     // market, limit, scale_in
    private String stopLogic;

    // Preparation guidance
    private String watchCondition;
    private String improvementPath;
    private String keyLevelToWatch;
    private String timeframeGuidance;

    // Scores
    private Integer alignmentScore;
    private Integer confidenceScore;

    // Whether a tradeable setup was found
    private boolean setupFound;
    private String noSetupReason;      // Why no setup was found (if setupFound=false)
}

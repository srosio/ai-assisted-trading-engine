package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI-generated execution plan for intraday trade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    // Execution model
    private String executionModel; // market, limit, scale_in

    // Entry zone
    private BigDecimal entryZoneLow;
    private BigDecimal entryZoneHigh;

    // Stop placement logic
    private String stopLogic; // e.g., "below swept low", "below recent structure"
    private BigDecimal suggestedStopPrice;

    // Targets with R multiples
    private List<Target> targets;

    // Invalidation conditions
    private List<String> invalidationConditions;

    // Risk notes
    private List<String> riskNotes;

    // Execution notes
    private String executionNotes;

    /**
     * Target with R multiple
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Target {
        private BigDecimal price;
        private Double rMultiple; // e.g., 2.0 for 2R
        private String logic; // e.g., "HTF resistance", "Fibonacci extension"
    }
}

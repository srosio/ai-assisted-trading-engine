package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedAiAnalysis {

    // Setup Assessment fields
    private String setupQuality;  // A, B, or C
    private List<String> riskFactors;
    private String invalidation;
    private String summary;
    private Integer alignmentScore;
    private String keyObservation;

    // Execution Plan fields (only populated if quality is A or B)
    private String executionModel;
    private BigDecimal entryZoneLow;
    private BigDecimal entryZoneHigh;
    private String stopLogic;
    private BigDecimal suggestedStopPrice;
    private List<ExecutionPlan.Target> targets;
    private List<String> invalidationConditions;
    private List<String> riskNotes;
    private String executionNotes;

    public AiAssessment toAiAssessment() {
        return AiAssessment.builder()
                .setupQuality(setupQuality)
                .riskFactors(riskFactors)
                .invalidation(invalidation)
                .summary(summary)
                .alignmentScore(alignmentScore)
                .keyObservation(keyObservation)
                .build();
    }

    public ExecutionPlan toExecutionPlan() {
        return ExecutionPlan.builder()
                .executionModel(executionModel)
                .entryZoneLow(entryZoneLow)
                .entryZoneHigh(entryZoneHigh)
                .stopLogic(stopLogic)
                .suggestedStopPrice(suggestedStopPrice)
                .targets(targets)
                .invalidationConditions(invalidationConditions)
                .riskNotes(riskNotes)
                .executionNotes(executionNotes)
                .build();
    }
}

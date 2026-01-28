package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAssessment {

    // Core assessment fields
    private String setupQuality;
    private List<String> riskFactors;
    private String invalidation;
    private String summary;
    private Integer alignmentScore;
    private String keyObservation;

    // Preparation guidance (for B/C quality setups)
    private String watchCondition;           // Primary thing to monitor
    private String improvementPath;          // What would upgrade quality
    private List<String> preparationSteps;   // Actionable checklist
    private String alternativeEntry;         // Different entry approach
    private String keyLevelToWatch;          // Important price level
    private String timeframeGuidance;        // When to re-evaluate

    // Contextual reasoning
    private String htfConflictExplanation;   // Explain HTF alignment issue
    private String oiBehaviorInsight;        // Interpret OI data
}

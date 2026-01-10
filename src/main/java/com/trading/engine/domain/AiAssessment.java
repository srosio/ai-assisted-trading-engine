package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("setup_quality")
    private String setupQuality; // A, B, or C

    @JsonProperty("risk_factors")
    private List<String> riskFactors;

    private String invalidation; // What would invalidate this setup

    private String summary; // Brief factual summary

    @JsonProperty("alignment_score")
    private Integer alignmentScore; // 0-100, how well conditions align with the model

    @JsonProperty("key_observation")
    private String keyObservation; // Most important thing to watch
}

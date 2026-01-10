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

    private String setupQuality;

    private List<String> riskFactors;

    private String invalidation;

    private String summary;

    private Integer alignmentScore;

    private String keyObservation;
}

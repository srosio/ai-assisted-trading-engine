package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of rule engine validation.
 * Rules are deterministic and non-negotiable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {

    private boolean passed;

    @Builder.Default
    private List<String> passedRules = new ArrayList<>();

    @Builder.Default
    private List<String> failedRules = new ArrayList<>();

    private String summary;

    /**
     * Add a passed rule check
     */
    public void addPassedRule(String rule) {
        if (passedRules == null) {
            passedRules = new ArrayList<>();
        }
        passedRules.add(rule);
    }

    /**
     * Add a failed rule check
     */
    public void addFailedRule(String rule) {
        if (failedRules == null) {
            failedRules = new ArrayList<>();
        }
        failedRules.add(rule);
        this.passed = false;
    }
}

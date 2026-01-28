package com.trading.engine.service;

import com.trading.engine.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Classifies signals into TRADE, WATCH, or BLOCKED categories
 */
@Service
@Slf4j
public class SignalClassificationService {

    /**
     * Classify signal into TRADE, WATCH, or BLOCKED
     */
    public SignalType classify(TradeSignal signal) {
        final var ruleResult = signal.getRuleResult();
        final var aiAssessment = signal.getAiAssessment();
        final var checklist = signal.getExecutionChecklist();

        // Check for hard blockers first
        if (hasHardBlock(ruleResult, checklist)) {
            log.info("Signal {} classified as BLOCKED - hard rule violation", signal.getSignalId());
            return SignalType.BLOCKED;
        }

        // Check if ready to trade
        if (isTradeReady(signal)) {
            log.info("Signal {} classified as TRADE - all conditions met", signal.getSignalId());
            return SignalType.TRADE;
        }

        // Check if has improvement potential
        if (hasImprovementPotential(signal)) {
            log.info("Signal {} classified as WATCH - has improvement path", signal.getSignalId());
            return SignalType.WATCH;
        }

        // Default to blocked if no viable path
        log.info("Signal {} classified as BLOCKED - no viable improvement path", signal.getSignalId());
        return SignalType.BLOCKED;
    }

    /**
     * Hard blocks that cannot be worked around
     */
    private boolean hasHardBlock(RuleResult ruleResult, ExecutionChecklist checklist) {
        if (ruleResult != null && ruleResult.getFailedRules() != null) {
            for (String rule : ruleResult.getFailedRules()) {
                final var ruleLower = rule.toLowerCase();
                // These are non-negotiable hard blocks
                if (ruleLower.contains("daily loss limit") ||
                    ruleLower.contains("session blocked") ||
                    ruleLower.contains("position already open") ||
                    ruleLower.contains("asia session")) {
                    return true;
                }
            }
        }

        // Critical blockers from checklist
        if (checklist != null && checklist.getBlockers() != null) {
            for (String blocker : checklist.getBlockers()) {
                final var blockerLower = blocker.toLowerCase();
                if (blockerLower.contains("critical") ||
                    blockerLower.contains("extreme") ||
                    blockerLower.contains("insufficient liquidity")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Signal is ready to trade - all conditions met
     */
    private boolean isTradeReady(TradeSignal signal) {
        final var ai = signal.getAiAssessment();
        final var rules = signal.getRuleResult();

        // Quality A or B required
        boolean qualityOk = ai != null &&
            ("A".equals(ai.getSetupQuality()) || "B".equals(ai.getSetupQuality()));

        // Rules must pass
        boolean rulesPassed = rules != null && rules.isPassed();

        // Must have execution plan with entry zone
        boolean hasExecution = signal.getExecutionPlan() != null &&
            signal.getExecutionPlan().getEntryZoneLow() != null;

        return qualityOk && rulesPassed && hasExecution;
    }

    /**
     * Signal has potential to become tradeable
     */
    private boolean hasImprovementPotential(TradeSignal signal) {
        final var ai = signal.getAiAssessment();
        final var intraday = signal.getIntradayContext();

        if (ai == null) return false;

        // Quality B or C can potentially improve
        boolean qualityCanImprove = "B".equals(ai.getSetupQuality()) ||
            "C".equals(ai.getSetupQuality());

        if (!qualityCanImprove) return false;

        // Has improvement path from AI
        boolean hasPath = ai.getImprovementPath() != null &&
            !ai.getImprovementPath().isEmpty();

        // Has something to watch
        boolean hasWatchCondition = ai.getWatchCondition() != null &&
            !ai.getWatchCondition().isEmpty();

        // Confidence not too low (at least 40)
        boolean reasonableConfidence = intraday != null &&
            intraday.getConfidenceScore() != null &&
            intraday.getConfidenceScore() >= 40;

        // Need at least one actionable element and reasonable confidence
        return (hasPath || hasWatchCondition) && reasonableConfidence;
    }
}

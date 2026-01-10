package com.trading.engine.service;

import com.trading.engine.config.TradingConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Rule Engine with deterministic, non-negotiable validation.
 * Rules CANNOT be overridden by AI or anyone else.
 *
 * If rules fail, trade is BLOCKED regardless of AI assessment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final TradingConfig tradingConfig;
    private final JournalService journalService;

    /**
     * Validate setup against all rules
     */
    public RuleResult validateSetup(MarketContext context, AiAssessment aiAssessment) {
        log.info("Validating setup for {} - Quality: {}", context.getSymbol(), aiAssessment.getSetupQuality());

        RuleResult result = RuleResult.builder()
                .passed(true)
                .build();

        // Rule 1: Setup Quality
        validateSetupQuality(aiAssessment.getSetupQuality(), result);

        // Rule 2: HTF Alignment
        validateHtfAlignment(context, result);

        // Rule 3: Session
        validateSession(context.getSession(), result);

        // Rule 4: Max Trades Per Day
        validateMaxTrades(result);

        // Rule 5: Daily Loss Limit
        validateDailyLoss(result);

        // Rule 6: Open Interest
        validateOpenInterest(context.getOiChangePercent(), result);

        // Rule 7: Volatility
        validateVolatility(context.getVolatility(), result);

        // Build summary
        result.setSummary(buildSummary(result));

        log.info("Rule validation complete - Passed: {}, Failed rules: {}",
                result.isPassed(), result.getFailedRules().size());

        return result;
    }

    /**
     * Rule 1: Setup quality must be allowed
     */
    private void validateSetupQuality(String quality, RuleResult result) {
        boolean allowed = switch (quality) {
            case "A" -> tradingConfig.isAllowAQuality();
            case "B" -> tradingConfig.isAllowBQuality();
            case "C" -> tradingConfig.isAllowCQuality();
            default -> false;
        };

        if (allowed) {
            result.addPassedRule("Setup quality " + quality + " is allowed");
        } else {
            result.addFailedRule("Setup quality " + quality + " is not allowed");
        }
    }

    /**
     * Rule 2: HTF bias must align with direction (if required)
     */
    private void validateHtfAlignment(MarketContext context, RuleResult result) {
        if (!tradingConfig.isRequireHtfAlignment()) {
            result.addPassedRule("HTF alignment not required");
            return;
        }

        String event = context.getLiquidityEvent().toLowerCase();
        String htfBias = context.getHtfBias().toLowerCase();

        boolean isLongEvent = event.contains("long") || event.contains("bullish");
        boolean isShortEvent = event.contains("short") || event.contains("bearish");

        boolean aligned = false;
        if (isLongEvent && htfBias.equals("bullish")) {
            aligned = true;
        } else if (isShortEvent && htfBias.equals("bearish")) {
            aligned = true;
        } else if (htfBias.equals("neutral")) {
            aligned = true; // Neutral allows both directions
        }

        if (aligned) {
            result.addPassedRule("HTF bias (" + htfBias + ") aligns with event direction");
        } else {
            result.addFailedRule("HTF bias (" + htfBias + ") does not align with event direction");
        }
    }

    /**
     * Rule 3: Session must be enabled
     */
    private void validateSession(String session, RuleResult result) {
        if (session == null) {
            result.addFailedRule("Session not specified");
            return;
        }

        boolean allowed = switch (session.toUpperCase()) {
            case "LONDON" -> tradingConfig.isLondonSessionEnabled();
            case "NY", "NEW_YORK" -> tradingConfig.isNySessionEnabled();
            case "ASIA" -> tradingConfig.isAsiaSessionEnabled();
            default -> false;
        };

        if (allowed) {
            result.addPassedRule("Session " + session + " is enabled");
        } else {
            result.addFailedRule("Session " + session + " is not enabled");
        }
    }

    /**
     * Rule 4: Max trades per day not exceeded
     */
    private void validateMaxTrades(RuleResult result) {
        long todayCount = journalService.getTodayTradeCount();

        if (todayCount < tradingConfig.getMaxTradesPerDay()) {
            result.addPassedRule(String.format("Trades today (%d) below max (%d)",
                    todayCount, tradingConfig.getMaxTradesPerDay()));
        } else {
            result.addFailedRule(String.format("Max trades per day reached (%d/%d)",
                    todayCount, tradingConfig.getMaxTradesPerDay()));
        }
    }

    /**
     * Rule 5: Daily loss limit not exceeded
     */
    private void validateDailyLoss(RuleResult result) {
        // This would check actual P&L from journal
        // For now, simplified check
        boolean withinLimit = journalService.isDailyLossWithinLimit();

        if (withinLimit) {
            result.addPassedRule("Daily loss within limit");
        } else {
            result.addFailedRule("Daily loss limit exceeded (-" + tradingConfig.getMaxDailyLossR() + "R)");
        }
    }

    /**
     * Rule 6: Minimum Open Interest change
     */
    private void validateOpenInterest(Double oiChange, RuleResult result) {
        if (oiChange == null) {
            result.addFailedRule("Open Interest data unavailable");
            return;
        }

        if (Math.abs(oiChange) >= tradingConfig.getMinOiChangePercent()) {
            result.addPassedRule(String.format("OI change (%.2f%%) meets minimum", oiChange));
        } else {
            result.addFailedRule(String.format("OI change (%.2f%%) below minimum (%.2f%%)",
                    oiChange, tradingConfig.getMinOiChangePercent()));
        }
    }

    /**
     * Rule 7: Volatility within acceptable range
     */
    private void validateVolatility(String volatility, RuleResult result) {
        if (volatility == null || volatility.equals("unknown")) {
            result.addFailedRule("Volatility state unknown");
            return;
        }

        if (tradingConfig.isBlockHighVolatility() && volatility.equals("expanding")) {
            result.addFailedRule("High volatility blocked by configuration");
        } else {
            result.addPassedRule("Volatility (" + volatility + ") is acceptable");
        }
    }

    /**
     * Build human-readable summary
     */
    private String buildSummary(RuleResult result) {
        if (result.isPassed()) {
            return String.format("All %d rules passed. Setup is valid for consideration.",
                    result.getPassedRules().size());
        } else {
            return String.format("Setup BLOCKED. %d rule(s) failed: %s",
                    result.getFailedRules().size(),
                    String.join(", ", result.getFailedRules()));
        }
    }

    /**
     * Quick check if basic rules are likely to pass (for early filtering)
     */
    public boolean quickValidation(String session) {
        // Check session
        boolean sessionOk = switch (session.toUpperCase()) {
            case "LONDON" -> tradingConfig.isLondonSessionEnabled();
            case "NY", "NEW_YORK" -> tradingConfig.isNySessionEnabled();
            case "ASIA" -> tradingConfig.isAsiaSessionEnabled();
            default -> false;
        };

        // Check max trades
        long todayCount = journalService.getTodayTradeCount();
        boolean tradesOk = todayCount < tradingConfig.getMaxTradesPerDay();

        // Check daily loss
        boolean lossOk = journalService.isDailyLossWithinLimit();

        return sessionOk && tradesOk && lossOk;
    }
}

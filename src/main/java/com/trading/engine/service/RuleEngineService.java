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

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final TradingConfig tradingConfig;
    private final JournalService journalService;

    public RuleResult validateSetup(final MarketContext context, final AiAssessment aiAssessment) {
        log.info("Validating setup for {} - Quality: {}", context.getSymbol(), aiAssessment.getSetupQuality());

        final var result = RuleResult.builder()
                .passed(true)
                .build();

        validateSetupQuality(aiAssessment.getSetupQuality(), result);

        validateHtfAlignment(context, result);

        validateSession(context.getSession(), result);

        validateMaxTrades(result);

        validateDailyLoss(result);

        validateOpenInterest(context.getOiChangePercent(), result);

        validateVolatility(context.getVolatility(), result);

        result.setSummary(buildSummary(result));

        log.info("Rule validation complete - Passed: {}, Failed rules: {}",
                result.isPassed(), result.getFailedRules().size());

        return result;
    }

    private void validateSetupQuality(final String quality, final RuleResult result) {
        final var allowed = switch (quality) {
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

    private void validateHtfAlignment(final MarketContext context, final RuleResult result) {
        if (!tradingConfig.isRequireHtfAlignment()) {
            result.addPassedRule("HTF alignment not required");
            return;
        }

        final var event = context.getLiquidityEvent().toLowerCase();
        final var htfBias = context.getHtfBias().toLowerCase();

        final var isLongEvent = event.contains("long") || event.contains("bullish");
        final var isShortEvent = event.contains("short") || event.contains("bearish");

        var aligned = false;
        if (isLongEvent && htfBias.equals("bullish")) {
            aligned = true;
        } else if (isShortEvent && htfBias.equals("bearish")) {
            aligned = true;
        } else if (htfBias.equals("neutral")) {
            aligned = true;
        }

        if (aligned) {
            result.addPassedRule("HTF bias (" + htfBias + ") aligns with event direction");
        } else {
            result.addFailedRule("HTF bias (" + htfBias + ") does not align with event direction");
        }
    }

    private void validateSession(final String session, final RuleResult result) {
        if (session == null) {
            result.addFailedRule("Session not specified");
            return;
        }

        final var allowed = switch (session.toUpperCase()) {
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

    private void validateMaxTrades(final RuleResult result) {
        final var todayCount = journalService.getTodayTradeCount();

        if (todayCount < tradingConfig.getMaxTradesPerDay()) {
            result.addPassedRule(String.format("Trades today (%d) below max (%d)",
                    todayCount, tradingConfig.getMaxTradesPerDay()));
        } else {
            result.addFailedRule(String.format("Max trades per day reached (%d/%d)",
                    todayCount, tradingConfig.getMaxTradesPerDay()));
        }
    }

    private void validateDailyLoss(final RuleResult result) {
        final var withinLimit = journalService.isDailyLossWithinLimit();

        if (withinLimit) {
            result.addPassedRule("Daily loss within limit");
        } else {
            result.addFailedRule("Daily loss limit exceeded (-" + tradingConfig.getMaxDailyLossR() + "R)");
        }
    }

    private void validateOpenInterest(final Double oiChange, final RuleResult result) {
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

    private void validateVolatility(final String volatility, final RuleResult result) {
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

    private String buildSummary(final RuleResult result) {
        if (result.isPassed()) {
            return String.format("All %d rules passed. Setup is valid for consideration.",
                    result.getPassedRules().size());
        } else {
            return String.format("Setup BLOCKED. %d rule(s) failed: %s",
                    result.getFailedRules().size(),
                    String.join(", ", result.getFailedRules()));
        }
    }

    public boolean quickValidation(final String session) {
        final var sessionOk = switch (session.toUpperCase()) {
            case "LONDON" -> tradingConfig.isLondonSessionEnabled();
            case "NY", "NEW_YORK" -> tradingConfig.isNySessionEnabled();
            case "ASIA" -> tradingConfig.isAsiaSessionEnabled();
            default -> false;
        };

        final var todayCount = journalService.getTodayTradeCount();
        final var tradesOk = todayCount < tradingConfig.getMaxTradesPerDay();

        final var lossOk = journalService.isDailyLossWithinLimit();

        return sessionOk && tradesOk && lossOk;
    }
}

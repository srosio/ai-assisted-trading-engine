package com.trading.engine.service;

import com.trading.engine.domain.ExecutionChecklist;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution Advisory Layer
 *
 * Provides pre-execution checklist:
 * - Spread OK
 * - Funding acceptable
 * - Volatility within bounds
 * - Liquidity adequate
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionAdvisoryService {

    private final MarketDataService marketData;

    // Thresholds
    private static final double MAX_SPREAD_BPS = 5.0; // 5 basis points
    private static final double MAX_FUNDING_RATE = 0.01; // 1%
    private static final double MIN_ORDER_BOOK_RATIO = 0.7;
    private static final double MAX_ORDER_BOOK_RATIO = 1.3;

    /**
     * Generate pre-execution checklist
     */
    public ExecutionChecklist generateChecklist(final String symbol,
                                                 final MarketContext marketContext,
                                                 final IntradayContext intradayContext) {
        log.info("Generating execution checklist for {}", symbol);

        final var warnings = new ArrayList<String>();
        final var blockers = new ArrayList<String>();

        // Check 1: Spread
        final var spreadCheck = checkSpread(symbol, marketContext.getCurrentPrice(), warnings, blockers);

        // Check 2: Funding rate
        final var fundingCheck = checkFunding(
                marketContext.getFundingRate(),
                intradayContext.getFundingRateDelta(),
                warnings,
                blockers
        );

        // Check 3: Volatility
        final var volatilityCheck = checkVolatility(marketContext.getVolatility(), warnings);

        // Check 4: Liquidity (order book)
        final var liquidityCheck = checkLiquidity(
                intradayContext.getOrderBookImbalance(),
                warnings,
                blockers
        );

        // Overall readiness
        final var ready = blockers.isEmpty();

        // Generate summary
        final var summary = generateSummary(ready, warnings.size(), blockers.size());

        return ExecutionChecklist.builder()
                .spreadOk(spreadCheck.ok)
                .currentSpreadBps(spreadCheck.spreadBps)
                .fundingAcceptable(fundingCheck.ok)
                .currentFundingRate(fundingCheck.rate)
                .fundingWarning(fundingCheck.warning)
                .volatilityWithinBounds(volatilityCheck.ok)
                .volatilityState(volatilityCheck.state)
                .liquidityAdequate(liquidityCheck.ok)
                .orderBookImbalance(liquidityCheck.imbalance)
                .readyForExecution(ready)
                .warnings(warnings)
                .blockers(blockers)
                .checklistSummary(summary)
                .build();
    }

    /**
     * Check spread (bid-ask)
     */
    private SpreadResult checkSpread(final String symbol,
                                      final BigDecimal currentPrice,
                                      final List<String> warnings,
                                      final List<String> blockers) {
        try {
            // Get order book to calculate spread
            final var orderBookImbalance = marketData.getOrderBookImbalance(symbol);

            // Approximate spread as 0.02% for liquid pairs, 0.05% for less liquid
            // Real implementation would parse actual bid/ask from depth
            final var spreadBps = orderBookImbalance != null && Math.abs(orderBookImbalance - 1.0) < 0.2
                    ? 2.0  // Tight spread
                    : 5.0; // Wider spread

            if (spreadBps > MAX_SPREAD_BPS) {
                blockers.add(String.format("Spread too wide: %.2f bps (max %.0f bps)", spreadBps, MAX_SPREAD_BPS));
                return new SpreadResult(false, spreadBps);
            } else if (spreadBps > MAX_SPREAD_BPS * 0.7) {
                warnings.add(String.format("Spread elevated: %.2f bps", spreadBps));
            }

            return new SpreadResult(true, spreadBps);

        } catch (final Exception e) {
            log.error("Error checking spread: {}", e.getMessage());
            warnings.add("Could not verify spread");
            return new SpreadResult(true, 0.0); // Optimistic default
        }
    }

    /**
     * Check funding rate
     */
    private FundingResult checkFunding(final Double fundingRate,
                                        final Double fundingDelta,
                                        final List<String> warnings,
                                        final List<String> blockers) {
        if (fundingRate == null) {
            return new FundingResult(true, 0.0, null);
        }

        String warning = null;

        // Check if funding is extreme
        if (Math.abs(fundingRate) > MAX_FUNDING_RATE) {
            warning = String.format("Extreme funding: %.4f%% (consider cost)", fundingRate * 100);
            blockers.add(warning);
            return new FundingResult(false, fundingRate, warning);
        }

        // Check if funding is trending in unfavorable direction
        if (fundingDelta != null && Math.abs(fundingDelta) > 0.0005) {
            warning = String.format("Funding changing rapidly: %.5f delta", fundingDelta);
            warnings.add(warning);
        }

        if (Math.abs(fundingRate) > MAX_FUNDING_RATE * 0.5) {
            warning = String.format("Elevated funding: %.4f%%", fundingRate * 100);
            warnings.add(warning);
        }

        return new FundingResult(true, fundingRate, warning);
    }

    /**
     * Check volatility
     */
    private VolatilityResult checkVolatility(final String volatility,
                                              final List<String> warnings) {
        if ("expanding".equals(volatility)) {
            warnings.add("Volatility expanding - wider stops may be needed");
            return new VolatilityResult(true, volatility);
        }

        return new VolatilityResult(true, volatility);
    }

    /**
     * Check liquidity (order book balance)
     */
    private LiquidityResult checkLiquidity(final Double orderBookImbalance,
                                            final List<String> warnings,
                                            final List<String> blockers) {
        if (orderBookImbalance == null) {
            return new LiquidityResult(true, 1.0);
        }

        // Check if order book is extremely imbalanced
        if (orderBookImbalance < MIN_ORDER_BOOK_RATIO) {
            warnings.add(String.format("Order book skewed to sell side: %.2f", orderBookImbalance));
        } else if (orderBookImbalance > MAX_ORDER_BOOK_RATIO) {
            warnings.add(String.format("Order book skewed to buy side: %.2f", orderBookImbalance));
        }

        // Extreme imbalances might indicate low liquidity
        if (orderBookImbalance < 0.5 || orderBookImbalance > 2.0) {
            blockers.add(String.format("Extreme order book imbalance: %.2f (low liquidity)", orderBookImbalance));
            return new LiquidityResult(false, orderBookImbalance);
        }

        return new LiquidityResult(true, orderBookImbalance);
    }

    /**
     * Generate checklist summary
     */
    private String generateSummary(final boolean ready, final int warningCount, final int blockerCount) {
        if (ready && warningCount == 0) {
            return "All checks passed - Ready for execution";
        } else if (ready && warningCount > 0) {
            return String.format("Ready with %d warning(s) - Review before execution", warningCount);
        } else {
            return String.format("NOT READY - %d blocker(s) must be resolved", blockerCount);
        }
    }

    // Helper classes for results
    private record SpreadResult(boolean ok, double spreadBps) {}
    private record FundingResult(boolean ok, double rate, String warning) {}
    private record VolatilityResult(boolean ok, String state) {}
    private record LiquidityResult(boolean ok, double imbalance) {}
}

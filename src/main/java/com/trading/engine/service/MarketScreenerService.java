package com.trading.engine.service;

import com.trading.engine.config.ScannerConfig;
import com.trading.engine.domain.ScreenResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Technical pre-screening service that analyzes symbols using Binance data
 * to identify "interesting" market conditions worth sending to AI for deeper analysis.
 * This saves AI costs by filtering out quiet/uninteresting markets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketScreenerService {

    private final MarketDataService marketData;
    private final ScannerConfig scannerConfig;

    /**
     * Screen all watchlist symbols and return ranked results.
     * Only symbols passing the interest threshold are included.
     */
    public List<ScreenResult> screenWatchlist(final String session) {
        log.info("Screening {} symbols for {} session", scannerConfig.getWatchlist().size(), session);

        final var results = new ArrayList<ScreenResult>();

        for (final var symbol : scannerConfig.getWatchlist()) {
            try {
                final var result = screenSymbol(symbol);
                if (result.isInteresting()) {
                    results.add(result);
                    log.info("  {} - INTERESTING (score: {}) - {}", symbol, result.getInterestScore(), result.getSignals());
                } else {
                    log.debug("  {} - quiet (score: {})", symbol, result.getInterestScore());
                }
            } catch (final Exception e) {
                log.warn("Failed to screen {}: {}", symbol, e.getMessage());
            }
        }

        // Sort by interest score descending, limit to max symbols for AI
        results.sort(Comparator.comparingInt(ScreenResult::getInterestScore).reversed());

        final var maxSymbols = scannerConfig.getMaxSymbolsPerScan();
        if (results.size() > maxSymbols) {
            log.info("Limiting AI analysis to top {} of {} interesting symbols", maxSymbols, results.size());
            return results.subList(0, maxSymbols);
        }

        log.info("Screening complete: {}/{} symbols passed pre-screening", results.size(), scannerConfig.getWatchlist().size());
        return results;
    }

    /**
     * Screen a single symbol for interesting conditions.
     */
    public ScreenResult screenSymbol(final String symbol) {
        final var signals = new ArrayList<String>();
        var interestScore = 0;

        // Fetch market data
        final var currentPrice = marketData.getCurrentPrice(symbol);
        final var oiChange = marketData.getOpenInterestChangePercent(symbol);
        final var fundingRate = marketData.getFundingRate(symbol);
        final var volatilityState = marketData.getVolatilityState(symbol);
        final var atr = marketData.getATR(symbol, 14);
        final var dayHigh = marketData.get24hHigh(symbol);
        final var dayLow = marketData.get24hLow(symbol);
        final var takerRatio = marketData.getTakerBuySellRatio(symbol);
        final var orderBookImbalance = marketData.getOrderBookImbalance(symbol);

        // === Screening Criteria ===

        // 1. Open Interest changes (strong signal)
        if (oiChange != null && Math.abs(oiChange) >= scannerConfig.getMinOiChangeForScreen()) {
            if (Math.abs(oiChange) >= 5.0) {
                signals.add(String.format("Major OI shift (%+.1f%%)", oiChange));
                interestScore += 30;
            } else if (Math.abs(oiChange) >= 3.0) {
                signals.add(String.format("Significant OI change (%+.1f%%)", oiChange));
                interestScore += 20;
            } else {
                signals.add(String.format("OI movement (%+.1f%%)", oiChange));
                interestScore += 10;
            }
        }

        // 2. Funding rate extremes (potential squeeze setup)
        if (fundingRate != null) {
            final var fundingPercent = Math.abs(fundingRate) * 100;
            if (fundingPercent > 0.05) {
                signals.add(String.format("Extreme funding (%.4f%%)", fundingRate * 100));
                interestScore += 25;
            } else if (fundingPercent > 0.02) {
                signals.add(String.format("Elevated funding (%.4f%%)", fundingRate * 100));
                interestScore += 10;
            }
        }

        // 3. Price near key levels (previous day high/low)
        final var keyLevelProximity = scannerConfig.getKeyLevelProximityPercent();
        var distanceToKeyLevel = Double.MAX_VALUE;
        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0 && dayHigh != null && dayLow != null) {
            final var distToHigh = calculatePercentDistance(currentPrice, dayHigh);
            final var distToLow = calculatePercentDistance(currentPrice, dayLow);
            distanceToKeyLevel = Math.min(distToHigh, distToLow);

            if (distanceToKeyLevel <= keyLevelProximity) {
                if (distToHigh < distToLow) {
                    signals.add(String.format("Near 24h high (%.1f%% away)", distToHigh));
                } else {
                    signals.add(String.format("Near 24h low (%.1f%% away)", distToLow));
                }
                interestScore += 20;
            }

            // Price beyond range = potential breakout
            if (currentPrice.compareTo(dayHigh) > 0) {
                signals.add("Breaking above 24h high");
                interestScore += 25;
            } else if (currentPrice.compareTo(dayLow) < 0) {
                signals.add("Breaking below 24h low");
                interestScore += 25;
            }
        }

        // 4. Order book imbalance (smart money positioning)
        if (orderBookImbalance != null) {
            if (orderBookImbalance > 1.5) {
                signals.add(String.format("Strong bid imbalance (%.2f)", orderBookImbalance));
                interestScore += 15;
            } else if (orderBookImbalance < 0.67) {
                signals.add(String.format("Strong ask imbalance (%.2f)", orderBookImbalance));
                interestScore += 15;
            }
        }

        // 5. Taker ratio extremes
        if (takerRatio != null) {
            if (takerRatio > 1.15) {
                signals.add("Aggressive buying detected");
                interestScore += 10;
            } else if (takerRatio < 0.85) {
                signals.add("Aggressive selling detected");
                interestScore += 10;
            }
        }

        // 6. Volatility state
        if ("expanding".equals(volatilityState)) {
            signals.add("Volatility expanding");
            interestScore += 10;
        } else if ("contracting".equals(volatilityState)) {
            signals.add("Volatility contracting (squeeze potential)");
            interestScore += 15;
        }

        // Determine price location
        final var priceLocation = determinePriceLocation(currentPrice, dayHigh, dayLow);
        final var trendBias = determineTrendBias(currentPrice, dayHigh, dayLow, takerRatio, oiChange);

        // Threshold: 30+ = interesting
        final var interesting = interestScore >= 30;

        return ScreenResult.builder()
                .symbol(symbol)
                .interesting(interesting)
                .interestScore(Math.min(100, interestScore))
                .currentPrice(currentPrice)
                .previousDayHigh(dayHigh)
                .previousDayLow(dayLow)
                .oiChangePercent(oiChange)
                .fundingRate(fundingRate)
                .volatilityState(volatilityState)
                .atr(atr)
                .signals(signals)
                .nearestSupport(dayLow)
                .nearestResistance(dayHigh)
                .distanceToKeyLevel(distanceToKeyLevel == Double.MAX_VALUE ? null : distanceToKeyLevel)
                .volumeRatio(takerRatio)
                .takerRatio(takerRatio)
                .priceLocation(priceLocation)
                .trendBias(trendBias)
                .build();
    }

    private double calculatePercentDistance(final BigDecimal price, final BigDecimal level) {
        if (price == null || level == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return Double.MAX_VALUE;
        }
        return price.subtract(level).abs()
                .divide(price, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();
    }

    private String determinePriceLocation(final BigDecimal price, final BigDecimal high, final BigDecimal low) {
        if (price == null || high == null || low == null) return "unknown";

        if (price.compareTo(high) > 0) return "above_range";
        if (price.compareTo(low) < 0) return "below_range";

        final var range = high.subtract(low);
        if (range.compareTo(BigDecimal.ZERO) == 0) return "middle";

        final var position = price.subtract(low)
                .divide(range, 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        if (position.compareTo(new BigDecimal("66")) > 0) return "upper_third";
        if (position.compareTo(new BigDecimal("33")) < 0) return "lower_third";
        return "middle";
    }

    private String determineTrendBias(final BigDecimal price, final BigDecimal high, final BigDecimal low,
                                       final Double takerRatio, final Double oiChange) {
        var bullishSignals = 0;
        var bearishSignals = 0;

        // Price location
        if (price != null && high != null && low != null) {
            final var mid = high.add(low).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            if (price.compareTo(mid) > 0) bullishSignals++;
            else bearishSignals++;
        }

        // Taker pressure
        if (takerRatio != null) {
            if (takerRatio > 1.05) bullishSignals++;
            else if (takerRatio < 0.95) bearishSignals++;
        }

        // OI direction
        if (oiChange != null) {
            if (oiChange > 2.0) bullishSignals++;
            else if (oiChange < -2.0) bearishSignals++;
        }

        if (bullishSignals > bearishSignals) return "bullish";
        if (bearishSignals > bullishSignals) return "bearish";
        return "neutral";
    }
}

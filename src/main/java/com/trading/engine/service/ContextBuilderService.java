package com.trading.engine.service;

import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.TradingViewWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Builds structured, factual market context snapshots.
 * NO opinions, NO predictions - only objective data.
 * This context is fed to the AI for analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextBuilderService {

    private final MarketDataService marketDataService;

    /**
     * Build complete market context from webhook and live data
     */
    public MarketContext buildContext(TradingViewWebhook webhook) {
        log.info("Building market context for {} - Event: {}", webhook.getSymbol(), webhook.getEvent());

        String symbol = webhook.getSymbol();

        // Get live market data
        BigDecimal currentPrice = marketDataService.getCurrentPrice(symbol);
        Double oiChange = marketDataService.getOpenInterestChangePercent(symbol);
        Double fundingRate = marketDataService.getFundingRate(symbol);
        String volatility = marketDataService.getVolatilityState(symbol);
        BigDecimal atr = marketDataService.getATR(symbol, 14);

        // Get support/resistance levels (simplified - use previous day high/low)
        BigDecimal pdHigh = webhook.getPreviousDayHigh() != null
                ? webhook.getPreviousDayHigh()
                : marketDataService.get24hHigh(symbol);
        BigDecimal pdLow = webhook.getPreviousDayLow() != null
                ? webhook.getPreviousDayLow()
                : marketDataService.get24hLow(symbol);

        // Determine location relative to key levels
        String location = determineLocation(currentPrice, pdHigh, pdLow);

        // Build context
        return MarketContext.builder()
                .symbol(symbol)
                .htfBias(webhook.getHtfBias() != null ? webhook.getHtfBias() : "neutral")
                .location(location)
                .session(webhook.getSession())
                .oiChangePercent(oiChange)
                .fundingRate(fundingRate)
                .liquidityEvent(webhook.getEvent())
                .volatility(volatility)
                .currentPrice(currentPrice)
                .previousDayHigh(pdHigh)
                .previousDayLow(pdLow)
                .volumeSpike(webhook.getVolumeSpike() != null ? webhook.getVolumeSpike() : false)
                .displacementDetected(webhook.getDisplacementDetected() != null ? webhook.getDisplacementDetected() : false)
                .atrValue(atr)
                .nearestResistance(pdHigh)
                .nearestSupport(pdLow)
                .build();
    }

    /**
     * Determine price location relative to key levels
     */
    private String determineLocation(BigDecimal price, BigDecimal pdHigh, BigDecimal pdLow) {
        if (price.compareTo(pdHigh) > 0) {
            return "above_previous_day_high";
        } else if (price.compareTo(pdLow) < 0) {
            return "below_previous_day_low";
        } else {
            // Calculate position within range
            BigDecimal range = pdHigh.subtract(pdLow);
            BigDecimal position = price.subtract(pdLow);
            BigDecimal percentage = position.divide(range, 2, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));

            if (percentage.compareTo(new BigDecimal("66")) > 0) {
                return "upper_third_of_range";
            } else if (percentage.compareTo(new BigDecimal("33")) < 0) {
                return "lower_third_of_range";
            } else {
                return "middle_of_range";
            }
        }
    }

    /**
     * Validate that context has sufficient data quality
     */
    public boolean isContextValid(MarketContext context) {
        if (context.getCurrentPrice() == null || context.getCurrentPrice().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Invalid context: missing or zero price");
            return false;
        }

        if (context.getOiChangePercent() == null) {
            log.warn("Invalid context: missing OI data");
            return false;
        }

        if (context.getVolatility() == null || context.getVolatility().equals("unknown")) {
            log.warn("Invalid context: volatility calculation failed");
            return false;
        }

        return true;
    }
}

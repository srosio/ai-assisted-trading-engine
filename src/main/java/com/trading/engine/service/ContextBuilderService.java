package com.trading.engine.service;

import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.TradingViewWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContextBuilderService {

    private final MarketDataService marketDataService;

    public MarketContext buildContext(final TradingViewWebhook webhook) {
        log.info("Building market context for {} - Event: {}", webhook.getSymbol(), webhook.getEvent());

        final var symbol = webhook.getSymbol();

        // Try Binance API, fallback to webhook data
        BigDecimal currentPrice = webhook.getPrice();
        Double oiChange = BigDecimal.ZERO.doubleValue();
        BigDecimal fundingRate = BigDecimal.ZERO;
        String volatility = "normal";
        BigDecimal atr = BigDecimal.ZERO;

        try {
            final var binancePrice = marketDataService.getCurrentPrice(symbol);
            if (binancePrice != null && binancePrice.compareTo(BigDecimal.ZERO) > 0) {
                currentPrice = binancePrice;
            }

            final var binanceOI = marketDataService.getOpenInterestChangePercent(symbol);
            if (binanceOI != null) {
                oiChange = binanceOI;
            }

            final var binanceFunding = marketDataService.getFundingRate(symbol);
            if (binanceFunding != null) {
                fundingRate = binanceFunding;
            }

            final var binanceVolatility = marketDataService.getVolatilityState(symbol);
            if (binanceVolatility != null && !binanceVolatility.equals("unknown")) {
                volatility = binanceVolatility;
            }

            final var binanceATR = marketDataService.getATR(symbol, 14);
            if (binanceATR != null && binanceATR.compareTo(BigDecimal.ZERO) > 0) {
                atr = binanceATR;
            }

            log.info("Market data from Binance: price={}, OI={}%, volatility={}", currentPrice, oiChange, volatility);
        } catch (final Exception e) {
            log.warn("Binance API unavailable, using webhook data only: {}", e.getMessage());
        }

        final var pdHigh = webhook.getPreviousDayHigh() != null
                ? webhook.getPreviousDayHigh()
                : currentPrice.multiply(new BigDecimal("1.01")); // Fallback: 1% above current

        final var pdLow = webhook.getPreviousDayLow() != null
                ? webhook.getPreviousDayLow()
                : currentPrice.multiply(new BigDecimal("0.99")); // Fallback: 1% below current

        final var location = determineLocation(currentPrice, pdHigh, pdLow);

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

    private String determineLocation(final BigDecimal price, final BigDecimal pdHigh, final BigDecimal pdLow) {
        if (price.compareTo(pdHigh) > 0) {
            return "above_previous_day_high";
        } else if (price.compareTo(pdLow) < 0) {
            return "below_previous_day_low";
        } else {
            final var range = pdHigh.subtract(pdLow);
            final var position = price.subtract(pdLow);
            final var percentage = position.divide(range, 2, RoundingMode.HALF_UP)
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

    public boolean isContextValid(final MarketContext context) {
        // Only check essential fields - price and symbol
        if (context.getCurrentPrice() == null || context.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid context: missing or invalid price");
            return false;
        }

        if (context.getSymbol() == null || context.getSymbol().isEmpty()) {
            log.warn("Invalid context: missing symbol");
            return false;
        }

        // OI and volatility are nice-to-have but not required (can use fallback values)
        if (context.getOiChangePercent() == null) {
            log.info("Using fallback OI value (0%) - Binance data unavailable");
        }

        if (context.getVolatility() == null || context.getVolatility().equals("unknown")) {
            log.info("Using fallback volatility (normal) - Binance data unavailable");
        }

        return true;
    }
}

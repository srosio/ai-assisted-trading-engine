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

        final var currentPrice = marketDataService.getCurrentPrice(symbol);
        final var oiChange = marketDataService.getOpenInterestChangePercent(symbol);
        final var fundingRate = marketDataService.getFundingRate(symbol);
        final var volatility = marketDataService.getVolatilityState(symbol);
        final var atr = marketDataService.getATR(symbol, 14);

        final var pdHigh = webhook.getPreviousDayHigh() != null
                ? webhook.getPreviousDayHigh()
                : marketDataService.get24hHigh(symbol);
        final var pdLow = webhook.getPreviousDayLow() != null
                ? webhook.getPreviousDayLow()
                : marketDataService.get24hLow(symbol);

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

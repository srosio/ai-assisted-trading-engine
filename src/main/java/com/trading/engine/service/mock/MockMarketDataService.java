package com.trading.engine.service.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Mock Market Data Service
 * Returns simulated data when Binance API keys are not configured.
 *
 * Useful for:
 * - Testing the system without real API access
 * - Development environment
 * - Demo purposes
 */
@Service
@Primary
@ConditionalOnProperty(name = "mock.enabled", havingValue = "true")
@Slf4j
public class MockMarketDataService {

    private final Random random = new Random();

    public BigDecimal getCurrentPrice(String symbol) {
        log.debug("[MOCK] Getting current price for {}", symbol);

        // Return realistic fake prices based on symbol
        BigDecimal basePrice = switch (symbol) {
            case "BTCUSDT" -> new BigDecimal("43000");
            case "ETHUSDT" -> new BigDecimal("2200");
            case "BNBUSDT" -> new BigDecimal("300");
            default -> new BigDecimal("100");
        };

        // Add some random variation (+/- 2%)
        double variation = (random.nextDouble() - 0.5) * 0.04;
        return basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(variation)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Double getOpenInterestChangePercent(String symbol) {
        log.debug("[MOCK] Getting OI change for {}", symbol);
        // Return random OI change between -5% and +10%
        return -5.0 + (random.nextDouble() * 15.0);
    }

    public Double getFundingRate(String symbol) {
        log.debug("[MOCK] Getting funding rate for {}", symbol);
        // Return realistic funding rate between -0.1% and +0.1%
        return (random.nextDouble() - 0.5) * 0.002;
    }

    public BigDecimal getATR(String symbol, int periods) {
        log.debug("[MOCK] Calculating ATR for {} with {} periods", symbol, periods);

        // Return ATR as ~1-2% of current price
        BigDecimal price = getCurrentPrice(symbol);
        double atrPercent = 0.01 + (random.nextDouble() * 0.01); // 1-2%

        return price.multiply(BigDecimal.valueOf(atrPercent))
                .setScale(8, RoundingMode.HALF_UP);
    }

    public String getVolatilityState(String symbol) {
        log.debug("[MOCK] Getting volatility state for {}", symbol);

        // Return random volatility state
        int rand = random.nextInt(3);
        return switch (rand) {
            case 0 -> "expanding";
            case 1 -> "contracting";
            default -> "stable";
        };
    }

    public BigDecimal get24hHigh(String symbol) {
        log.debug("[MOCK] Getting 24h high for {}", symbol);
        BigDecimal current = getCurrentPrice(symbol);
        return current.multiply(new BigDecimal("1.05")); // 5% above current
    }

    public BigDecimal get24hLow(String symbol) {
        log.debug("[MOCK] Getting 24h low for {}", symbol);
        BigDecimal current = getCurrentPrice(symbol);
        return current.multiply(new BigDecimal("0.95")); // 5% below current
    }
}

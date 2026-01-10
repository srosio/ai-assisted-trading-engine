package com.trading.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.config.BinanceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for retrieving real market data from Binance Futures.
 * Provides: Price, Open Interest, Funding Rate, Volatility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final WebClient binanceWebClient;
    private final BinanceConfig binanceConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get current price for a symbol
     */
    @Cacheable(value = "prices", key = "#symbol")
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            String response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/price?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return new BigDecimal(node.get("price").asText());
        } catch (Exception e) {
            log.error("Error fetching price for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get Open Interest and calculate change
     */
    @Cacheable(value = "openInterest", key = "#symbol")
    public Double getOpenInterestChangePercent(String symbol) {
        try {
            // Get current OI
            String currentResponse = binanceWebClient.get()
                    .uri("/fapi/v1/openInterest?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode currentNode = objectMapper.readTree(currentResponse);
            double currentOI = currentNode.get("openInterest").asDouble();

            // Get historical OI from 1 hour ago (simplified - would use proper historical endpoint)
            // For now, we'll use a placeholder calculation
            // In production, fetch from /futures/data/openInterestHist
            double previousOI = currentOI * 0.98; // Placeholder

            double changePercent = ((currentOI - previousOI) / previousOI) * 100;
            return Math.round(changePercent * 100.0) / 100.0;

        } catch (Exception e) {
            log.error("Error fetching OI for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Get current funding rate
     */
    @Cacheable(value = "fundingRate", key = "#symbol")
    public Double getFundingRate(String symbol) {
        try {
            String response = binanceWebClient.get()
                    .uri("/fapi/v1/premiumIndex?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return node.get("lastFundingRate").asDouble();

        } catch (Exception e) {
            log.error("Error fetching funding rate for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculate ATR (Average True Range) from recent klines
     */
    @Cacheable(value = "atr", key = "#symbol")
    public BigDecimal getATR(String symbol, int periods) {
        try {
            String response = binanceWebClient.get()
                    .uri("/fapi/v1/klines?symbol=" + symbol + "&interval=5m&limit=" + (periods + 1))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode klines = objectMapper.readTree(response);
            List<BigDecimal> trueRanges = new ArrayList<>();

            BigDecimal previousClose = null;
            for (JsonNode kline : klines) {
                BigDecimal high = new BigDecimal(kline.get(2).asText());
                BigDecimal low = new BigDecimal(kline.get(3).asText());
                BigDecimal close = new BigDecimal(kline.get(4).asText());

                if (previousClose != null) {
                    BigDecimal tr1 = high.subtract(low);
                    BigDecimal tr2 = high.subtract(previousClose).abs();
                    BigDecimal tr3 = low.subtract(previousClose).abs();

                    BigDecimal trueRange = tr1.max(tr2).max(tr3);
                    trueRanges.add(trueRange);
                }
                previousClose = close;
            }

            // Calculate average
            BigDecimal sum = trueRanges.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(new BigDecimal(trueRanges.size()), 8, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.error("Error calculating ATR for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calculate volatility state based on ATR comparison
     */
    public String getVolatilityState(String symbol) {
        try {
            BigDecimal currentATR = getATR(symbol, 14);
            BigDecimal longTermATR = getATR(symbol, 50);

            if (longTermATR.compareTo(BigDecimal.ZERO) == 0) {
                return "stable";
            }

            BigDecimal ratio = currentATR.divide(longTermATR, 2, RoundingMode.HALF_UP);

            if (ratio.compareTo(new BigDecimal("1.2")) > 0) {
                return "expanding";
            } else if (ratio.compareTo(new BigDecimal("0.8")) < 0) {
                return "contracting";
            } else {
                return "stable";
            }

        } catch (Exception e) {
            log.error("Error calculating volatility for {}: {}", symbol, e.getMessage());
            return "unknown";
        }
    }

    /**
     * Get 24h high
     */
    public BigDecimal get24hHigh(String symbol) {
        try {
            String response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return new BigDecimal(node.get("highPrice").asText());

        } catch (Exception e) {
            log.error("Error fetching 24h high for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get 24h low
     */
    public BigDecimal get24hLow(String symbol) {
        try {
            String response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return new BigDecimal(node.get("lowPrice").asText());

        } catch (Exception e) {
            log.error("Error fetching 24h low for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}

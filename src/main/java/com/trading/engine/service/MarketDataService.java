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

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final WebClient binanceWebClient;
    private final BinanceConfig binanceConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Cacheable(value = "prices", key = "#symbol")
    public BigDecimal getCurrentPrice(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/price?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);
            return new BigDecimal(node.get("price").asText());
        } catch (final Exception e) {
            log.error("Error fetching price for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Cacheable(value = "openInterest", key = "#symbol")
    public Double getOpenInterestChangePercent(final String symbol) {
        try {
            final var currentResponse = binanceWebClient.get()
                    .uri("/fapi/v1/openInterest?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var currentNode = objectMapper.readTree(currentResponse);
            final var currentOI = currentNode.get("openInterest").asDouble();

            final var previousOI = currentOI * 0.98;

            final var changePercent = ((currentOI - previousOI) / previousOI) * 100;
            return Math.round(changePercent * 100.0) / 100.0;

        } catch (final Exception e) {
            log.error("Error fetching OI for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Cacheable(value = "fundingRate", key = "#symbol")
    public Double getFundingRate(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/premiumIndex?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);
            return node.get("lastFundingRate").asDouble();

        } catch (final Exception e) {
            log.error("Error fetching funding rate for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Cacheable(value = "atr", key = "#symbol")
    public BigDecimal getATR(final String symbol, final int periods) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/klines?symbol=" + symbol + "&interval=5m&limit=" + (periods + 1))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var klines = objectMapper.readTree(response);
            final List<BigDecimal> trueRanges = new ArrayList<>();

            BigDecimal previousClose = null;
            for (final var kline : klines) {
                final var high = new BigDecimal(kline.get(2).asText());
                final var low = new BigDecimal(kline.get(3).asText());
                final var close = new BigDecimal(kline.get(4).asText());

                if (previousClose != null) {
                    final var tr1 = high.subtract(low);
                    final var tr2 = high.subtract(previousClose).abs();
                    final var tr3 = low.subtract(previousClose).abs();

                    final var trueRange = tr1.max(tr2).max(tr3);
                    trueRanges.add(trueRange);
                }
                previousClose = close;
            }

            final var sum = trueRanges.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(new BigDecimal(trueRanges.size()), 8, RoundingMode.HALF_UP);

        } catch (final Exception e) {
            log.error("Error calculating ATR for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public String getVolatilityState(final String symbol) {
        try {
            final var currentATR = getATR(symbol, 14);
            final var longTermATR = getATR(symbol, 50);

            if (longTermATR.compareTo(BigDecimal.ZERO) == 0) {
                return "stable";
            }

            final var ratio = currentATR.divide(longTermATR, 2, RoundingMode.HALF_UP);

            if (ratio.compareTo(new BigDecimal("1.2")) > 0) {
                return "expanding";
            } else if (ratio.compareTo(new BigDecimal("0.8")) < 0) {
                return "contracting";
            } else {
                return "stable";
            }

        } catch (final Exception e) {
            log.error("Error calculating volatility for {}: {}", symbol, e.getMessage());
            return "unknown";
        }
    }

    public BigDecimal get24hHigh(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);
            return new BigDecimal(node.get("highPrice").asText());

        } catch (final Exception e) {
            log.error("Error fetching 24h high for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal get24hLow(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);
            return new BigDecimal(node.get("lowPrice").asText());

        } catch (final Exception e) {
            log.error("Error fetching 24h low for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}

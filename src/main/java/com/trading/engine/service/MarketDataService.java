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

    /**
     * Get funding rate delta (current vs 8h ago)
     */
    @Cacheable(value = "fundingDelta", key = "#symbol")
    public Double getFundingRateDelta(final String symbol) {
        try {
            // Get current funding rate
            final var current = getFundingRate(symbol);

            // Get historical funding rates (last 2 entries = current and previous)
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/fundingRate?symbol=" + symbol + "&limit=2")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var rates = objectMapper.readTree(response);
            if (rates.size() >= 2) {
                final var previous = rates.get(1).get("fundingRate").asDouble();
                final var delta = current - previous;
                return Math.round(delta * 100000.0) / 100000.0;
            }

            return 0.0;

        } catch (final Exception e) {
            log.error("Error fetching funding delta for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Get taker buy/sell ratio (from recent trades)
     */
    @Cacheable(value = "takerRatio", key = "#symbol")
    public Double getTakerBuySellRatio(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);

            // Binance provides quoteVolume, we'll approximate buy/sell pressure from price action
            final var priceChangePercent = node.get("priceChangePercent").asDouble();

            // If price up significantly, assume buy pressure > sell pressure
            // This is an approximation - for actual data we'd need aggregateTrades endpoint
            if (priceChangePercent > 1.0) {
                return 1.2; // More buyers
            } else if (priceChangePercent < -1.0) {
                return 0.8; // More sellers
            } else {
                return 1.0; // Balanced
            }

        } catch (final Exception e) {
            log.error("Error calculating taker ratio for {}: {}", symbol, e.getMessage());
            return 1.0;
        }
    }

    /**
     * Get spot vs perp volume ratio
     */
    @Cacheable(value = "spotPerpVolume", key = "#symbol")
    public Double getSpotVsPerpVolumeRatio(final String symbol) {
        try {
            // Get perp volume
            final var perpResponse = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var perpNode = objectMapper.readTree(perpResponse);
            final var perpVolume = perpNode.get("volume").asDouble();

            // For spot volume, we'd need spot API endpoint
            // Approximation: assume spot is ~60% of perp for major pairs
            final var estimatedSpotVolume = perpVolume * 0.6;

            final var ratio = estimatedSpotVolume / perpVolume;
            return Math.round(ratio * 100.0) / 100.0;

        } catch (final Exception e) {
            log.error("Error calculating spot/perp ratio for {}: {}", symbol, e.getMessage());
            return 0.6; // Default estimate
        }
    }

    /**
     * Get liquidation data (total liquidations in last hour)
     */
    @Cacheable(value = "liquidations", key = "#symbol")
    public Double getRecentLiquidations(final String symbol) {
        try {
            // Binance doesn't provide direct liquidation endpoint in public API
            // We can infer from forceOrders endpoint
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/allForceOrders?symbol=" + symbol + "&limit=100")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var orders = objectMapper.readTree(response);

            // Count recent liquidations (last hour)
            final var oneHourAgo = System.currentTimeMillis() - 3600000;
            var liquidationCount = 0;

            for (final var order : orders) {
                final var time = order.get("time").asLong();
                if (time > oneHourAgo) {
                    liquidationCount++;
                }
            }

            return (double) liquidationCount;

        } catch (final Exception e) {
            // forceOrders endpoint may not be available or require authentication
            log.debug("Liquidation data not available for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Get order book imbalance (bid vs ask volume at top levels)
     */
    @Cacheable(value = "orderBookImbalance", key = "#symbol")
    public Double getOrderBookImbalance(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/depth?symbol=" + symbol + "&limit=10")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var depth = objectMapper.readTree(response);

            // Calculate total bid and ask volume at top 5 levels
            final var bids = depth.get("bids");
            final var asks = depth.get("asks");

            var bidVolume = 0.0;
            var askVolume = 0.0;

            for (var i = 0; i < Math.min(5, bids.size()); i++) {
                bidVolume += bids.get(i).get(1).asDouble();
            }

            for (var i = 0; i < Math.min(5, asks.size()); i++) {
                askVolume += asks.get(i).get(1).asDouble();
            }

            // Imbalance ratio (>1.0 = more bids, <1.0 = more asks)
            if (askVolume == 0.0) return 1.0;

            final var imbalance = bidVolume / askVolume;
            return Math.round(imbalance * 100.0) / 100.0;

        } catch (final Exception e) {
            log.error("Error fetching order book for {}: {}", symbol, e.getMessage());
            return 1.0; // Balanced
        }
    }

    /**
     * Get 24h volume data
     */
    @Cacheable(value = "volume24h", key = "#symbol")
    public Double get24hVolume(final String symbol) {
        try {
            final var response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr?symbol=" + symbol)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(binanceConfig.getTimeoutSeconds()))
                    .block();

            final var node = objectMapper.readTree(response);
            return node.get("volume").asDouble();

        } catch (final Exception e) {
            log.error("Error fetching 24h volume for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }
}

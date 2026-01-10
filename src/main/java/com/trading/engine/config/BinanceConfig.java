package com.trading.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Binance Futures API integration.
 * Used for real-time market data retrieval.
 */
@Configuration
@ConfigurationProperties(prefix = "binance")
@Data
public class BinanceConfig {

    private String apiKey;

    private String apiSecret;

    private String baseUrl = "https://fapi.binance.com";

    private Integer cacheSeconds = 10; // Cache market data for 10 seconds

    private Integer timeoutSeconds = 10;

    @Bean
    public WebClient binanceWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-MBX-APIKEY", apiKey != null ? apiKey : "")
                .build();
    }
}

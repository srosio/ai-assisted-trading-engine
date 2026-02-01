package com.trading.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "trading")
@Data
public class TradingConfig {

    // Risk Management
    private BigDecimal minRiskRewardRatio = new BigDecimal("3.0");

    // Session Rules
    private boolean londonSessionEnabled = true;
    private boolean nySessionEnabled = true;
    private boolean asiaSessionEnabled = false;

    // Setup Quality Rules
    private boolean allowAQuality = true;
    private boolean allowBQuality = true;
    private boolean allowCQuality = false; // C quality setups blocked by default

    // HTF Alignment
    private boolean requireHtfAlignment = true;

    // Allow counter-trend signals for reversal strategies (Candle 2, Liquidity Sweep)
    // When true, reversal events can trade against HTF bias
    private boolean allowReversalCounterTrend = true;

    // Volatility
    private boolean blockHighVolatility = true;
    private Double maxVolatilityThreshold = 2.5; // Multiple of average

    // Open Interest
    private Double minOiChangePercent = 2.0; // Minimum OI change to consider valid
}

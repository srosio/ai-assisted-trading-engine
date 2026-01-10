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
    private BigDecimal minRiskPercent = new BigDecimal("0.5");
    private BigDecimal maxRiskPercent = new BigDecimal("1.0");
    private BigDecimal defaultRiskPercent = new BigDecimal("1.0");

    private BigDecimal minRiskRewardRatio = new BigDecimal("3.0");
    private Integer maxTradesPerDay = 2;
    private BigDecimal maxDailyLossR = new BigDecimal("2.0"); // Max 2R loss per day

    // Account
    private BigDecimal accountBalance = new BigDecimal("10000"); // Default, should be configured

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

    // Volatility
    private boolean blockHighVolatility = true;
    private Double maxVolatilityThreshold = 2.5; // Multiple of average

    // Open Interest
    private Double minOiChangePercent = 2.0; // Minimum OI change to consider valid
}

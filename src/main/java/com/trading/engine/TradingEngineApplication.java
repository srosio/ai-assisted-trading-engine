package com.trading.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI-Assisted Crypto Trading Engine
 *
 * A professional rule-based trading system that uses AI ONLY for market context analysis.
 * AI cannot make trading decisions, modify risk, or override rules.
 *
 * Architecture:
 * TradingView Webhook → Context Builder → AI Analysis → Rule Engine → Risk Engine → Notification
 *
 * Core Principles:
 * 1. AI assists, never decides
 * 2. Rules are non-negotiable
 * 3. Risk management is sacred
 * 4. Everything is journaled
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAsync
public class TradingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingEngineApplication.class, args);
    }
}

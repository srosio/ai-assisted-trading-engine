package com.trading.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAsync
public class TradingEngineApplication {

    public static void main(final String[] args) {
        SpringApplication.run(TradingEngineApplication.class, args);
    }
}

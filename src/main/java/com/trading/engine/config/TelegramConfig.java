package com.trading.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "telegram")
@Data
public class TelegramConfig {

    private String botToken;

    private String chatId;

    private Boolean enabled = true;

    private Integer retryAttempts = 3;
}

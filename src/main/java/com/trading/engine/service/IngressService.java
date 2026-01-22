package com.trading.engine.service;

import com.trading.engine.domain.TradingViewWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {

    private final ConcurrentHashMap<String, Long> eventCache = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 300_000;

    public boolean isDuplicate(final TradingViewWebhook webhook) {
        final var eventKey = generateEventKey(webhook);
        final var now = System.currentTimeMillis();

        eventCache.entrySet().removeIf(entry -> now - entry.getValue() > DEDUP_WINDOW_MS);

        final var existingTimestamp = eventCache.putIfAbsent(eventKey, now);

        if (existingTimestamp != null) {
            log.warn("Duplicate event detected: {} (last seen {}ms ago)",
                    eventKey, now - existingTimestamp);
            return true;
        }

        return false;
    }

    private String generateEventKey(final TradingViewWebhook webhook) {
        return String.format("%s:%s:%s:%s:%s:%.2f",
                webhook.getSymbol(),
                webhook.getStrategy(),
                webhook.getEventType(),
                webhook.getDirection(),
                webhook.getSession(),
                webhook.getCurrentPrice() != null ? webhook.getCurrentPrice().doubleValue() : 0.0);
    }

    public ZonedDateTime getExchangeTime() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public String determineSession(final ZonedDateTime exchangeTime) {
        final var hour = exchangeTime.getHour();

        if (hour >= 13 && hour < 21) {
            return "NY";
        } else if (hour >= 8 && hour < 16) {
            return "LONDON";
        } else {
            return "ASIA";
        }
    }

    public boolean isValidPayload(final TradingViewWebhook webhook) {
        if (webhook.getSymbol() == null || webhook.getSymbol().isBlank()) {
            log.error("Invalid payload: missing symbol");
            return false;
        }

        if (webhook.getStrategy() == null || webhook.getStrategy().isBlank()) {
            log.error("Invalid payload: missing strategy");
            return false;
        }

        if (webhook.getEventType() == null || webhook.getEventType().isBlank()) {
            log.error("Invalid payload: missing event_type");
            return false;
        }

        if (webhook.getDirection() == null || webhook.getDirection().isBlank()) {
            log.error("Invalid payload: missing direction");
            return false;
        }

        if (webhook.getPrice() == null || webhook.getCurrentPrice() == null) {
            log.error("Invalid payload: missing price object or close price");
            return false;
        }

        return true;
    }

    public String processIngress(final TradingViewWebhook webhook) {
        final var exchangeTime = getExchangeTime();
        final var session = determineSession(exchangeTime);

        log.info("Ingress processing - Exchange time: {}, Determined session: {}",
                exchangeTime, session);

        return session;
    }
}

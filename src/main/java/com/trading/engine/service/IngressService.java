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

/**
 * Ingress Layer Service
 * - Payload validation
 * - Time alignment (exchange time)
 * - Session tagging
 * - Event deduplication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngressService {

    // Simple in-memory deduplication cache (for last 5 minutes of events)
    private final ConcurrentHashMap<String, Long> eventCache = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 300_000; // 5 minutes

    /**
     * Check if this event is a duplicate within the deduplication window
     */
    public boolean isDuplicate(final TradingViewWebhook webhook) {
        final var eventKey = generateEventKey(webhook);
        final var now = System.currentTimeMillis();

        // Clean old entries
        eventCache.entrySet().removeIf(entry -> now - entry.getValue() > DEDUP_WINDOW_MS);

        // Check if exists
        final var existingTimestamp = eventCache.putIfAbsent(eventKey, now);

        if (existingTimestamp != null) {
            log.warn("Duplicate event detected: {} (last seen {}ms ago)",
                    eventKey, now - existingTimestamp);
            return true;
        }

        return false;
    }

    /**
     * Generate unique key for event deduplication
     */
    private String generateEventKey(final TradingViewWebhook webhook) {
        return String.format("%s:%s:%s:%s:%s:%.2f",
                webhook.getSymbol(),
                webhook.getStrategy(),
                webhook.getEventType(),
                webhook.getDirection(),
                webhook.getSession(),
                webhook.getCurrentPrice() != null ? webhook.getCurrentPrice().doubleValue() : 0.0);
    }

    /**
     * Get exchange-aligned time (UTC)
     */
    public ZonedDateTime getExchangeTime() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    /**
     * Determine session based on exchange time (UTC)
     * This overrides webhook session if provided
     */
    public String determineSession(final ZonedDateTime exchangeTime) {
        final var hour = exchangeTime.getHour();

        // Session times in UTC
        // Asia: 00:00 - 08:00 UTC
        // London: 08:00 - 16:00 UTC
        // NY: 13:00 - 21:00 UTC (overlaps with London 13:00-16:00)

        if (hour >= 13 && hour < 21) {
            return "NY";
        } else if (hour >= 8 && hour < 16) {
            return "LONDON";
        } else {
            return "ASIA";
        }
    }

    /**
     * Validate payload has required fields
     */
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

    /**
     * Process webhook through ingress layer
     * Returns session tag to use
     */
    public String processIngress(final TradingViewWebhook webhook) {
        final var exchangeTime = getExchangeTime();
        final var session = determineSession(exchangeTime);

        log.info("Ingress processing - Exchange time: {}, Determined session: {}",
                exchangeTime, session);

        return session;
    }
}

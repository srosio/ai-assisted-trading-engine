package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Universal TradingView webhook model supporting both legacy and new schema formats.
 * Automatically maps old field names to new standardized structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingViewWebhook {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Timeframe is required")
    private String timeframe;

    // === New Universal Schema Fields ===

    /**
     * Strategy name (e.g., "Liquidity Sweeps", "Candle 2 Closure RSI")
     */
    private String strategy;

    /**
     * Event type: "reversal", "continuation", "breakout", "sweep"
     */
    @JsonProperty("event_type")
    private String eventType;

    /**
     * Direction: "bullish" or "bearish"
     */
    private String direction;

    /**
     * Current close price
     */
    @JsonProperty("price")
    @NotNull(message = "Price is required")
    private BigDecimal currentPrice;

    /**
     * Suggested stop loss level from strategy
     */
    @JsonProperty("stop_loss")
    private BigDecimal suggestedStopLoss;

    /**
     * Swept price level (for liquidity sweep events)
     */
    @JsonProperty("swept_level")
    private BigDecimal sweptLevel;

    /**
     * Higher timeframe bias: "bullish" or "bearish"
     */
    @JsonProperty("htf_bias")
    private String htfBias;

    /**
     * Displacement detected flag
     */
    private Boolean displacement;

    /**
     * Volume spike detected flag
     */
    @JsonProperty("volume_spike")
    private Boolean volumeSpike;

    /**
     * Previous day high for context
     */
    @JsonProperty("previous_day_high")
    private BigDecimal previousDayHigh;

    /**
     * Previous day low for context
     */
    @JsonProperty("previous_day_low")
    private BigDecimal previousDayLow;

    /**
     * Trading session
     */
    @NotBlank(message = "Session is required")
    private String session;

    /**
     * Event timestamp (ISO-8601)
     */
    private Instant timestamp;

    // === Legacy Support Fields (for backward compatibility) ===

    /**
     * Legacy event field (e.g., "liquidity_sweep_long")
     * Maps to new schema via strategy + event_type + direction
     */
    @JsonAlias("event")
    private String legacyEvent;

    /**
     * Legacy price field - maps to currentPrice
     */
    @JsonAlias("price")
    private BigDecimal legacyPrice;

    /**
     * Legacy sweptHigh - used to determine swept_level
     */
    private BigDecimal sweptHigh;

    /**
     * Legacy sweptLow - used to determine swept_level
     */
    private BigDecimal sweptLow;

    /**
     * Legacy displacementDetected - maps to displacement
     */
    private Boolean displacementDetected;

    /**
     * Get the effective event identifier (for backward compatibility)
     */
    public String getEvent() {
        if (legacyEvent != null) {
            return legacyEvent;
        }
        // Construct event from new schema
        if (strategy != null && eventType != null && direction != null) {
            return String.format("%s_%s_%s",
                strategy.toLowerCase().replace(" ", "_"),
                eventType,
                direction);
        }
        return eventType != null ? eventType : "unknown";
    }

    /**
     * Get the effective price (supports both schemas)
     */
    public BigDecimal getPrice() {
        if (currentPrice != null) {
            return currentPrice;
        }
        return legacyPrice;
    }

    /**
     * Check if using new universal schema format
     */
    public boolean isUniversalSchema() {
        return strategy != null && eventType != null && direction != null;
    }

    /**
     * Normalize legacy format to universal schema
     */
    public void normalizeLegacyFormat() {
        if (isUniversalSchema()) {
            return; // Already using new format
        }

        // Map legacy price field
        if (legacyPrice != null && currentPrice == null) {
            currentPrice = legacyPrice;
        }

        // Map legacy displacement field
        if (displacementDetected != null && displacement == null) {
            displacement = displacementDetected;
        }

        // Determine swept level from legacy fields
        if (sweptLevel == null) {
            if (sweptHigh != null && sweptHigh.compareTo(BigDecimal.ZERO) > 0) {
                sweptLevel = sweptHigh;
                direction = "bullish";
                eventType = "sweep";
            } else if (sweptLow != null && sweptLow.compareTo(BigDecimal.ZERO) > 0) {
                sweptLevel = sweptLow;
                direction = "bearish";
                eventType = "sweep";
            }
        }

        // Parse legacy event field
        if (legacyEvent != null && eventType == null) {
            parseLegacyEvent(legacyEvent);
        }

        // Default strategy if not set
        if (strategy == null) {
            strategy = "Legacy Strategy";
        }

        // Set timestamp if not provided
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Parse legacy event string to extract type and direction
     */
    private void parseLegacyEvent(String event) {
        String eventLower = event.toLowerCase();

        // Determine direction
        if (eventLower.contains("long") || eventLower.contains("bullish")) {
            direction = "bullish";
        } else if (eventLower.contains("short") || eventLower.contains("bearish")) {
            direction = "bearish";
        }

        // Determine event type
        if (eventLower.contains("sweep")) {
            eventType = "sweep";
        } else if (eventLower.contains("breakout") || eventLower.contains("break")) {
            eventType = "breakout";
        } else if (eventLower.contains("reversal") || eventLower.contains("closure")) {
            eventType = "reversal";
        } else if (eventLower.contains("continuation") || eventLower.contains("pullback")) {
            eventType = "continuation";
        } else {
            eventType = "unknown";
        }
    }
}

package com.trading.engine.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
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
 * Supports both nested (price/context objects) and flat field formats.
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
     * Strategy name (e.g., "Liquidity Sweeps", "Candle 2 Closure")
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
     * Trading session
     */
    @NotBlank(message = "Session is required")
    private String session;

    /**
     * Event timestamp (ISO-8601)
     */
    private Instant timestamp;

    // === Nested Price Object (Preferred Format) ===

    /**
     * Price information (nested object format)
     */
    private PriceInfo price;

    /**
     * Context information (nested object format)
     */
    private ContextInfo context;

    // === Flat Format Fields (Alternative/Legacy) ===

    /**
     * Current close price (flat format alternative to price.close)
     */
    @JsonProperty("currentPrice")
    private BigDecimal currentPrice;

    /**
     * Suggested stop loss (flat format alternative to price.stop_loss)
     */
    @JsonProperty("suggestedStopLoss")
    private BigDecimal suggestedStopLoss;

    /**
     * Swept level (flat format alternative to context.swept_level)
     */
    @JsonProperty("sweptLevel")
    private BigDecimal sweptLevel;

    /**
     * HTF bias (flat format alternative to context.htf_bias)
     */
    @JsonProperty("htfBias")
    private String htfBias;

    /**
     * Displacement (flat format alternative to context.displacement)
     */
    private Boolean displacement;

    /**
     * Volume spike (flat format alternative to context.volume_spike)
     */
    @JsonProperty("volumeSpike")
    private Boolean volumeSpike;

    /**
     * Previous day high (flat format alternative to context.previous_day_high)
     */
    @JsonProperty("previousDayHigh")
    private BigDecimal previousDayHigh;

    /**
     * Previous day low (flat format alternative to context.previous_day_low)
     */
    @JsonProperty("previousDayLow")
    private BigDecimal previousDayLow;

    // === Legacy Support Fields (for backward compatibility) ===

    /**
     * Legacy event field (e.g., "liquidity_sweep_long")
     * Maps to new schema via strategy + event_type + direction
     */
    @JsonProperty("event")
    private String legacyEvent;

    /**
     * Legacy single price field - maps to currentPrice
     */
    @JsonAlias("price")
    private BigDecimal legacyPriceField;

    /**
     * Legacy sweptHigh - used to determine swept_level
     */
    @JsonProperty("sweptHigh")
    private BigDecimal sweptHigh;

    /**
     * Legacy sweptLow - used to determine swept_level
     */
    @JsonProperty("sweptLow")
    private BigDecimal sweptLow;

    /**
     * Legacy displacementDetected - maps to displacement
     */
    @JsonProperty("displacementDetected")
    private Boolean displacementDetected;

    // === Nested Object Classes ===

    /**
     * Price information (nested format)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceInfo {

        @JsonProperty("close")
        private BigDecimal close;

        @JsonProperty("stop_loss")
        private BigDecimal stopLoss;
    }

    /**
     * Context information (nested format)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextInfo {

        @JsonProperty("swept_level")
        private BigDecimal sweptLevel;

        @JsonProperty("htf_bias")
        private String htfBias;

        @JsonProperty("displacement")
        private Boolean displacement;

        @JsonProperty("volume_spike")
        private Boolean volumeSpike;

        @JsonProperty("previous_day_high")
        private BigDecimal previousDayHigh;

        @JsonProperty("previous_day_low")
        private BigDecimal previousDayLow;
    }

    // === Custom Setter for Price (handles both nested object and flat number) ===

    /**
     * Custom setter to handle both nested price object and legacy flat price field
     */
    @JsonSetter("price")
    public void setPrice(Object priceValue) {
        if (priceValue instanceof PriceInfo) {
            this.price = (PriceInfo) priceValue;
        } else if (priceValue instanceof Number) {
            // Legacy format: "price": 92100.50
            this.legacyPriceField = new BigDecimal(priceValue.toString());
        } else if (priceValue instanceof java.util.Map) {
            // Handle as map and convert to PriceInfo
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) priceValue;
            PriceInfo priceInfo = new PriceInfo();
            if (map.containsKey("close")) {
                priceInfo.setClose(new BigDecimal(map.get("close").toString()));
            }
            if (map.containsKey("stop_loss")) {
                priceInfo.setStopLoss(new BigDecimal(map.get("stop_loss").toString()));
            }
            this.price = priceInfo;
        }
    }

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
     * Get the effective close price (supports nested, flat, and legacy formats)
     */
    public BigDecimal getCurrentPrice() {
        // Priority: nested price.close > flat currentPrice > legacy price field
        if (price != null && price.getClose() != null) {
            return price.getClose();
        }
        if (currentPrice != null) {
            return currentPrice;
        }
        return legacyPriceField;
    }

    /**
     * Get the suggested stop loss (supports nested and flat formats)
     */
    public BigDecimal getSuggestedStopLoss() {
        // Priority: nested price.stop_loss > flat suggestedStopLoss
        if (price != null && price.getStopLoss() != null) {
            return price.getStopLoss();
        }
        return suggestedStopLoss;
    }

    /**
     * Get the swept level (supports nested and flat formats)
     */
    public BigDecimal getSweptLevel() {
        // Priority: nested context.swept_level > flat sweptLevel > legacy sweptHigh/sweptLow
        if (context != null && context.getSweptLevel() != null) {
            return context.getSweptLevel();
        }
        if (sweptLevel != null) {
            return sweptLevel;
        }
        if (sweptHigh != null && sweptHigh.compareTo(BigDecimal.ZERO) > 0) {
            return sweptHigh;
        }
        if (sweptLow != null && sweptLow.compareTo(BigDecimal.ZERO) > 0) {
            return sweptLow;
        }
        return null;
    }

    /**
     * Get HTF bias (supports nested and flat formats)
     */
    public String getHtfBias() {
        // Priority: nested context.htf_bias > flat htfBias
        if (context != null && context.getHtfBias() != null) {
            return context.getHtfBias();
        }
        return htfBias;
    }

    /**
     * Get displacement flag (supports nested and flat formats)
     */
    public Boolean getDisplacement() {
        // Priority: nested context.displacement > flat displacement > legacy displacementDetected
        if (context != null && context.getDisplacement() != null) {
            return context.getDisplacement();
        }
        if (displacement != null) {
            return displacement;
        }
        return displacementDetected;
    }

    /**
     * Get volume spike flag (supports nested and flat formats)
     */
    public Boolean getVolumeSpike() {
        // Priority: nested context.volume_spike > flat volumeSpike
        if (context != null && context.getVolumeSpike() != null) {
            return context.getVolumeSpike();
        }
        return volumeSpike;
    }

    /**
     * Get previous day high (supports nested and flat formats)
     */
    public BigDecimal getPreviousDayHigh() {
        // Priority: nested context.previous_day_high > flat previousDayHigh
        if (context != null && context.getPreviousDayHigh() != null) {
            return context.getPreviousDayHigh();
        }
        return previousDayHigh;
    }

    /**
     * Get previous day low (supports nested and flat formats)
     */
    public BigDecimal getPreviousDayLow() {
        // Priority: nested context.previous_day_low > flat previousDayLow
        if (context != null && context.getPreviousDayLow() != null) {
            return context.getPreviousDayLow();
        }
        return previousDayLow;
    }

    /**
     * Get the effective price (for backward compatibility with old code)
     * @deprecated Use getCurrentPrice() instead
     */
    @Deprecated
    public BigDecimal getPrice() {
        return getCurrentPrice();
    }

    /**
     * Check if using new universal schema format
     */
    public boolean isUniversalSchema() {
        return strategy != null && eventType != null && direction != null;
    }

    /**
     * Normalize legacy format to universal schema.
     * Converts flat fields and legacy format to standardized structure.
     */
    public void normalizeLegacyFormat() {
        if (isUniversalSchema()) {
            return; // Already using new format
        }

        // Map legacy single price field to currentPrice if neither nested nor flat is set
        if (legacyPriceField != null && currentPrice == null && (price == null || price.getClose() == null)) {
            currentPrice = legacyPriceField;
        }

        // Map legacy displacement field
        if (displacementDetected != null && displacement == null && (context == null || context.getDisplacement() == null)) {
            displacement = displacementDetected;
        }

        // Determine swept level from legacy fields
        BigDecimal effectiveSweptLevel = getSweptLevel(); // Use getter to check all sources
        if (effectiveSweptLevel == null) {
            if (sweptHigh != null && sweptHigh.compareTo(BigDecimal.ZERO) > 0) {
                sweptLevel = sweptHigh;
                if (direction == null) direction = "bullish";
                if (eventType == null) eventType = "sweep";
            } else if (sweptLow != null && sweptLow.compareTo(BigDecimal.ZERO) > 0) {
                sweptLevel = sweptLow;
                if (direction == null) direction = "bearish";
                if (eventType == null) eventType = "sweep";
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

package com.trading.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to manage Binance API rate limiting using a token bucket algorithm.
 * Monitors API usage through X-MBX-USED-WEIGHT headers and implements proactive throttling.
 */
@Service
@Slf4j
public class RateLimitService {

    // Rate limit: 1200 weight per minute (standard tier)
    private static final int MAX_WEIGHT_PER_MINUTE = 1200;
    private static final int SAFE_WEIGHT_THRESHOLD = 1000; // Stay below 83% of limit

    // Track current weight usage per minute
    private final AtomicInteger currentWeight = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    // Track when we hit rate limits to implement back-off
    private final AtomicLong rateLimitHitTime = new AtomicLong(0);
    private final AtomicInteger consecutiveRateLimitHits = new AtomicInteger(0);

    // Store per-endpoint weight tracking
    private final ConcurrentHashMap<String, Integer> endpointWeights = new ConcurrentHashMap<>();

    public RateLimitService() {
        // Initialize default endpoint weights (these can be updated from actual API responses)
        endpointWeights.put("/fapi/v1/ticker/price", 1);
        endpointWeights.put("/fapi/v1/openInterest", 1);
        endpointWeights.put("/fapi/v1/premiumIndex", 1);
        endpointWeights.put("/fapi/v1/klines", 1);
        endpointWeights.put("/fapi/v1/ticker/24hr", 1);
        endpointWeights.put("/fapi/v1/fundingRate", 1);
        endpointWeights.put("/fapi/v1/allForceOrders", 5);
        endpointWeights.put("/fapi/v1/depth", 5);
    }

    public boolean tryAcquire(String endpoint) {
        resetIfNeeded();

        // Check if we're in a back-off period
        long backoffUntil = rateLimitHitTime.get();
        if (backoffUntil > 0 && System.currentTimeMillis() < backoffUntil) {
            long waitTime = backoffUntil - System.currentTimeMillis();
            log.warn("In back-off period, waiting {}ms before next request", waitTime);
            return false;
        }

        // Estimate the weight of this request
        int estimatedWeight = endpointWeights.getOrDefault(getEndpointKey(endpoint), 1);

        // Check if adding this request would exceed our safe threshold
        int currentUsage = currentWeight.get();
        if (currentUsage + estimatedWeight > SAFE_WEIGHT_THRESHOLD) {
            log.warn("Current weight usage ({}) + request weight ({}) would exceed safe threshold ({}). Delaying request.",
                    currentUsage, estimatedWeight, SAFE_WEIGHT_THRESHOLD);
            return false;
        }

        // Reserve the weight for this request
        currentWeight.addAndGet(estimatedWeight);
        return true;
    }

    public void acquireWithWait(String endpoint) throws InterruptedException {
        while (!tryAcquire(endpoint)) {
            // Wait before retrying
            long backoffUntil = rateLimitHitTime.get();
            long waitTime;

            if (backoffUntil > 0 && System.currentTimeMillis() < backoffUntil) {
                waitTime = backoffUntil - System.currentTimeMillis();
            } else {
                // Wait until the next minute window
                long timeSinceReset = System.currentTimeMillis() - lastResetTime.get();
                waitTime = Math.max(60000 - timeSinceReset, 1000);
            }

            log.info("Rate limit approaching, waiting {}ms before next request", waitTime);
            Thread.sleep(Math.min(waitTime, 5000)); // Max 5 second wait per iteration
        }
    }

    public void updateWeight(String weightHeader) {
        try {
            if (weightHeader != null && !weightHeader.isEmpty()) {
                int reportedWeight = Integer.parseInt(weightHeader);
                currentWeight.set(reportedWeight);

                if (reportedWeight > SAFE_WEIGHT_THRESHOLD) {
                    log.warn("API weight usage is high: {}/{}", reportedWeight, MAX_WEIGHT_PER_MINUTE);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse weight header: {}", weightHeader);
        }
    }

    public void recordRateLimitHit(Long retryAfterSeconds) {
        int hits = consecutiveRateLimitHits.incrementAndGet();

        // Calculate back-off time: use Retry-After if provided, otherwise exponential back-off
        long backoffMs;
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            backoffMs = retryAfterSeconds * 1000;
            log.warn("Rate limit hit (attempt #{}). Retry-After: {}s", hits, retryAfterSeconds);
        } else {
            // Exponential back-off: 2^hits seconds (max 5 minutes)
            backoffMs = Math.min((long) Math.pow(2, hits) * 1000, 300000);
            log.warn("Rate limit hit (attempt #{}). Using exponential back-off: {}ms", hits, backoffMs);
        }

        // Set the back-off period
        rateLimitHitTime.set(System.currentTimeMillis() + backoffMs);

        // Reset the weight counter to be safe
        currentWeight.set(MAX_WEIGHT_PER_MINUTE);
    }

    public void recordSuccess() {
        consecutiveRateLimitHits.set(0);
    }

    private void resetIfNeeded() {
        long now = System.currentTimeMillis();
        long lastReset = lastResetTime.get();

        // Reset every minute
        if (now - lastReset >= 60000) {
            if (lastResetTime.compareAndSet(lastReset, now)) {
                int previousWeight = currentWeight.getAndSet(0);
                log.debug("Rate limit window reset. Previous weight: {}", previousWeight);
            }
        }
    }

    private String getEndpointKey(String endpoint) {
        if (endpoint == null) return "";

        // Extract path without query parameters
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex > 0) {
            return endpoint.substring(0, queryIndex);
        }
        return endpoint;
    }

    public int getCurrentWeight() {
        return currentWeight.get();
    }

    public int getMaxWeight() {
        return MAX_WEIGHT_PER_MINUTE;
    }
}

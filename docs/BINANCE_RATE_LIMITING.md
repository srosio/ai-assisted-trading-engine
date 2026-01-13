# Binance API Rate Limiting Implementation

## Overview

This document describes the comprehensive rate limiting solution implemented to prevent Binance API errors (HTTP 418 and 429).

## Problem

Binance API enforces strict rate limits:
- **Weight-based limits**: 1200 weight per minute for standard tier
- **HTTP 418**: IP banned due to excessive violations
- **HTTP 429**: Too many requests

When these limits are exceeded, the API returns error responses and may temporarily ban the IP address.

## Solution Components

### 1. RateLimitService (`src/main/java/com/trading/engine/service/RateLimitService.java`)

Manages rate limiting using a token bucket algorithm.

**Features:**
- **Proactive throttling**: Tracks current weight usage and prevents requests from exceeding 83% of the limit (1000/1200)
- **Weight tracking per endpoint**: Maintains a map of endpoint weights based on Binance API documentation
- **Automatic reset**: Resets weight counters every minute (rolling window)
- **Back-off strategy**: Implements exponential back-off when rate limits are hit
- **Retry-After header support**: Respects the `Retry-After` header from Binance responses

**Key Methods:**
```java
// Non-blocking check if request can proceed
boolean tryAcquire(String endpoint)

// Blocking wait until request can proceed (sleeps if necessary)
void acquireWithWait(String endpoint) throws InterruptedException

// Update weight from API response header
void updateWeight(String weightHeader)

// Record rate limit hit and apply back-off
void recordRateLimitHit(Long retryAfterSeconds)

// Record successful request (resets consecutive failures)
void recordSuccess()
```

**Rate Limit Logic:**
- Safe threshold: 1000 weight (83% of 1200 max)
- Resets every 60 seconds
- Exponential back-off: 2^n seconds (max 5 minutes)
- Consecutive failure tracking

### 2. BinanceRateLimitFilter (`src/main/java/com/trading/engine/config/BinanceRateLimitFilter.java`)

WebClient filter that intercepts all requests and responses.

**Features:**
- **Pre-request rate limiting**: Calls `RateLimitService.acquireWithWait()` before each request
- **Response header monitoring**: Extracts `X-MBX-USED-WEIGHT-*` headers and updates the rate limiter
- **Error detection**: Identifies HTTP 418 and 429 responses
- **Automatic retry**: Uses Spring WebFlux Retry with exponential back-off
- **Retry-After support**: Reads and respects the `Retry-After` header

**Retry Configuration:**
- Max retry attempts: 3
- Initial delay: 1 second
- Max delay: 30 seconds
- Exponential back-off multiplier: 2x

**Monitored Headers:**
- `X-MBX-USED-WEIGHT-1M`: 1-minute weight usage
- `X-MBX-USED-WEIGHT-10S`: 10-second weight usage
- `Retry-After`: How long to wait before retrying

### 3. BinanceConfig Update

The WebClient is now configured with the rate limit filter:

```java
@Bean
public WebClient binanceWebClient() {
    return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-MBX-APIKEY", apiKey != null ? apiKey : "")
            .filter(rateLimitFilter)  // <- Rate limiting filter
            .build();
}
```

## How It Works

### Request Flow

```
1. Application calls MarketDataService method
2. RateLimitFilter intercepts the request
3. RateLimitService checks if request can proceed
   - If safe: allows request
   - If near limit: waits until safe
   - If in back-off: waits for back-off period to end
4. Request is sent to Binance API
5. Response headers are monitored:
   - X-MBX-USED-WEIGHT-* → Update weight tracking
   - HTTP 418/429 → Trigger back-off and retry
6. Successful response → Reset consecutive failures
7. Return data to application
```

### Rate Limit Scenarios

#### Scenario 1: Normal Operation
- Current weight: 300/1200
- New request weight: 5
- Action: Request proceeds immediately

#### Scenario 2: Approaching Limit
- Current weight: 1050/1200 (exceeds 1000 safe threshold)
- New request weight: 5
- Action: Wait until next minute window (auto-reset)

#### Scenario 3: Rate Limit Hit (HTTP 429)
- Response: HTTP 429 with `Retry-After: 120`
- Action:
  1. Set back-off period to 120 seconds
  2. Block all requests for 120 seconds
  3. Retry the failed request after back-off

#### Scenario 4: IP Ban (HTTP 418)
- Response: HTTP 418
- Action:
  1. Calculate exponential back-off (2^n seconds)
  2. Block all requests during back-off
  3. Log error and alert monitoring

## Configuration

### application.yml

```yaml
binance:
  api-key: ${BINANCE_API_KEY:mock-key}
  api-secret: ${BINANCE_API_SECRET:mock-secret}
  base-url: https://fapi.binance.com
  cache-seconds: 10      # Redis cache reduces duplicate requests
  timeout-seconds: 10
```

### Environment Variables

Required:
- `BINANCE_API_KEY`: Your Binance API key
- `BINANCE_API_SECRET`: Your Binance API secret

### Endpoint Weights

Default weights (configurable in `RateLimitService`):

| Endpoint | Weight |
|----------|--------|
| `/fapi/v1/ticker/price` | 1 |
| `/fapi/v1/openInterest` | 1 |
| `/fapi/v1/premiumIndex` | 1 |
| `/fapi/v1/klines` | 1 |
| `/fapi/v1/ticker/24hr` | 1 |
| `/fapi/v1/fundingRate` | 1 |
| `/fapi/v1/allForceOrders` | 5 |
| `/fapi/v1/depth` | 5 |

## Additional Optimizations

### 1. Redis Caching
- All `@Cacheable` methods in `MarketDataService` cache results for 10 seconds
- Reduces duplicate API calls for the same data
- Effective for high-frequency webhook processing

### 2. Event Deduplication
- `IngressService` deduplicates webhook events within 5-minute windows
- Prevents processing the same signal multiple times
- Further reduces API load

### 3. Async Processing
- Webhook processing uses async thread pool (5 core, 10 max threads)
- Prevents blocking the main thread
- Allows graceful handling of rate-limited requests

## Monitoring and Logging

### Log Messages

**Normal operation:**
```
DEBUG Rate limit window reset. Previous weight: 234
DEBUG Rate limit header X-MBX-USED-WEIGHT-1M: 234
```

**Approaching limit:**
```
WARN Current weight usage (1050) + request weight (5) would exceed safe threshold (1000). Delaying request.
INFO Rate limit approaching, waiting 5000ms before next request
```

**Rate limit hit:**
```
ERROR Rate limit exceeded! Status: 429, Retry-After: 120s, Endpoint: /fapi/v1/ticker/price
WARN Rate limit hit (attempt #1). Retry-After: 120s
INFO Retrying request (attempt 1/3): Rate limit exceeded (HTTP 429)
```

**Back-off period:**
```
WARN In back-off period, waiting 45000ms before next request
```

### Metrics to Monitor

1. **Current weight usage**: `RateLimitService.getCurrentWeight()`
2. **Consecutive rate limit hits**: Track 429/418 responses
3. **Back-off frequency**: How often back-off is triggered
4. **Request delays**: Time spent waiting for rate limits

## Testing

### Unit Tests

Test the rate limiting logic:
```java
// Test that requests are delayed when approaching limit
// Test that back-off is applied correctly
// Test that Retry-After header is respected
// Test that weight tracking updates from headers
```

### Integration Tests

Test with Binance API:
```java
// Send burst of requests and verify throttling
// Trigger rate limit and verify back-off
// Verify cache reduces API calls
```

### Load Testing

Simulate high-volume scenarios:
- Multiple simultaneous webhook events
- Rapid market data requests
- Recovery after rate limit hit

## Troubleshooting

### Issue: Still getting 418/429 errors

**Possible causes:**
1. Multiple applications using the same API key
2. Shared IP address with other users
3. Weight estimates are incorrect

**Solutions:**
1. Use dedicated API keys per application
2. Reduce `SAFE_WEIGHT_THRESHOLD` (currently 1000, try 800)
3. Update endpoint weights in `RateLimitService` constructor

### Issue: Requests are too slow

**Possible causes:**
1. Over-conservative rate limiting
2. Cache not working properly
3. Too many retries

**Solutions:**
1. Increase `SAFE_WEIGHT_THRESHOLD` (max 1150)
2. Verify Redis is running and cache is enabled
3. Reduce `MAX_RETRY_ATTEMPTS` in filter

### Issue: Back-off period too long

**Possible causes:**
1. Exponential back-off accumulating
2. Not respecting Retry-After header

**Solutions:**
1. Check `consecutiveRateLimitHits` counter
2. Verify Retry-After header parsing in filter
3. Adjust max back-off time (currently 5 minutes)

## Best Practices

1. **Monitor weight usage**: Set up alerts for weight > 900
2. **Use caching aggressively**: Cache static data (exchange info, symbol info)
3. **Batch requests**: Combine multiple data points into single calls where possible
4. **Use WebSockets**: For real-time data, WebSockets are more efficient than polling
5. **Separate API keys**: Use different keys for different services/environments
6. **Test in staging**: Verify rate limiting works before production deployment

## Future Enhancements

1. **Dynamic weight learning**: Learn actual weights from API responses
2. **Multi-tier rate limiting**: Support different account tiers (VIP, etc.)
3. **Distributed rate limiting**: Coordinate across multiple instances using Redis
4. **WebSocket integration**: Migrate real-time data to WebSocket connections
5. **Metrics dashboard**: Visualize rate limit usage and violations
6. **Alerting**: Send notifications when approaching rate limits

## References

- [Binance API Rate Limits](https://binance-docs.github.io/apidocs/futures/en/#limits)
- [Binance Error Codes](https://binance-docs.github.io/apidocs/futures/en/#error-codes)
- [Spring WebFlux Retry](https://projectreactor.io/docs/core/release/reference/#_retrying)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

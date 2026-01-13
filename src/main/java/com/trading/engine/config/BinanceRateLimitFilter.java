package com.trading.engine.config;

import com.trading.engine.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * WebClient filter for Binance API that implements:
 * 1. Request rate limiting before sending
 * 2. Response header monitoring (X-MBX-USED-WEIGHT)
 * 3. Automatic retry with exponential backoff for 418/429 errors
 * 4. Retry-After header support
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceRateLimitFilter implements ExchangeFilterFunction {

    private final RateLimitService rateLimitService;

    private static final String WEIGHT_HEADER_PREFIX = "X-MBX-USED-WEIGHT";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.defer(() -> {
            String endpoint = request.url().getPath();

            // Apply rate limiting before making the request
            try {
                rateLimitService.acquireWithWait(endpoint);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Rate limit wait interrupted", e);
                return Mono.error(e);
            }

            // Execute the request
            return next.exchange(request)
                    .flatMap(response -> handleResponse(response, request, next))
                    .doOnError(error -> log.error("Request failed for {}: {}", endpoint, error.getMessage()));
        })
        .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(30))
                .filter(this::isRetryableError)
                .doBeforeRetry(retrySignal -> {
                    log.info("Retrying request (attempt {}/{}): {}",
                            retrySignal.totalRetries() + 1,
                            MAX_RETRY_ATTEMPTS,
                            retrySignal.failure().getMessage());
                })
        );
    }

    /**
     * Handle the response, checking for rate limit errors and monitoring usage headers.
     */
    private Mono<ClientResponse> handleResponse(ClientResponse response, ClientRequest request, ExchangeFunction next) {
        // Extract and monitor weight usage headers
        response.headers().asHttpHeaders().forEach((headerName, headerValues) -> {
            if (headerName.startsWith(WEIGHT_HEADER_PREFIX) && !headerValues.isEmpty()) {
                String weightValue = headerValues.get(0);
                log.debug("Rate limit header {}: {}", headerName, weightValue);
                rateLimitService.updateWeight(weightValue);
            }
        });

        HttpStatus status = (HttpStatus) response.statusCode();

        // Handle rate limit errors (418 = IP banned, 429 = too many requests)
        if (status == HttpStatus.I_AM_A_TEAPOT || status == HttpStatus.TOO_MANY_REQUESTS) {
            String retryAfterHeader = response.headers().asHttpHeaders().getFirst(RETRY_AFTER_HEADER);
            Long retryAfterSeconds = parseRetryAfter(retryAfterHeader);

            log.error("Rate limit exceeded! Status: {}, Retry-After: {}s, Endpoint: {}",
                    status.value(), retryAfterSeconds != null ? retryAfterSeconds : "not specified",
                    request.url().getPath());

            // Record the rate limit hit in the service
            rateLimitService.recordRateLimitHit(retryAfterSeconds);

            // Return error that will trigger retry
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        String errorMsg = String.format("Rate limit exceeded (HTTP %d): %s", status.value(), body);
                        return Mono.error(new RateLimitException(errorMsg, retryAfterSeconds));
                    });
        }

        // For successful responses, record success and reset back-off
        if (status.is2xxSuccessful()) {
            rateLimitService.recordSuccess();
        }

        return Mono.just(response);
    }

    /**
     * Parse the Retry-After header (can be seconds or HTTP date).
     */
    private Long parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.isEmpty()) {
            return null;
        }

        try {
            // Try parsing as seconds (most common for Binance)
            return Long.parseLong(retryAfter);
        } catch (NumberFormatException e) {
            // Could be an HTTP date, but Binance typically uses seconds
            log.warn("Could not parse Retry-After header: {}", retryAfter);
            return null;
        }
    }

    /**
     * Determine if an error should trigger a retry.
     */
    private boolean isRetryableError(Throwable throwable) {
        // Retry on rate limit exceptions
        if (throwable instanceof RateLimitException) {
            return true;
        }

        // Retry on network errors
        String message = throwable.getMessage();
        return message != null && (
                message.contains("timeout") ||
                message.contains("Connection refused") ||
                message.contains("Connection reset")
        );
    }

    /**
     * Custom exception for rate limit errors.
     */
    public static class RateLimitException extends RuntimeException {
        private final Long retryAfterSeconds;

        public RateLimitException(String message, Long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}

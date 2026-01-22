package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.domain.*;
import com.trading.engine.repository.AiCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private final AiCacheRepository cacheRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public CombinedAiAnalysis get(MarketContext context, TradingViewWebhook webhook, IntradayContext intradayContext) {
        final var key = generateCacheKey(context, webhook, intradayContext);

        try {
            final var cached = cacheRepository.get(key);

            if (cached != null) {
                // DynamoDB TTL handles expiration automatically, but check anyway for safety
                final var age = Duration.between(
                        Instant.ofEpochSecond(cached.getTtl()).minus(CACHE_TTL),
                        Instant.now()
                );

                if (age.compareTo(CACHE_TTL) > 0) {
                    log.debug("Cache entry expired for {}", key);
                    cacheRepository.delete(key);
                    return null;
                }

                // Deserialize cached response
                final var response = objectMapper.readValue(
                        cached.getResponse(),
                        CombinedAiAnalysis.class
                );

                log.info("Cache HIT for {} - Strategy: {}, Quality: {} (age: {}s)",
                        webhook.getSymbol(), webhook.getStrategy(),
                        response.getSetupQuality(), age.getSeconds());

                return response;
            }

            return null;

        } catch (Exception e) {
            log.warn("Failed to retrieve from cache: {}", e.getMessage());
            return null;  // Cache miss on error
        }
    }

    public void put(MarketContext context, TradingViewWebhook webhook,
                    IntradayContext intradayContext, CombinedAiAnalysis response) {
        try {
            final var key = generateCacheKey(context, webhook, intradayContext);
            final var responseJson = objectMapper.writeValueAsString(response);

            // Calculate TTL (Unix epoch seconds) for DynamoDB
            final var ttl = Instant.now().plus(CACHE_TTL).getEpochSecond();

            final var entry = AiCacheEntry.builder()
                    .cacheKey(key)
                    .response(responseJson)
                    .createdAt(LocalDateTime.now().toString())
                    .ttl(ttl)
                    .symbol(webhook.getSymbol())
                    .strategy(webhook.getStrategy())
                    .quality(response.getSetupQuality())
                    .build();

            cacheRepository.put(entry);

            log.debug("Cached AI response for {} - Quality: {} (TTL: {}s)",
                    webhook.getSymbol(), response.getSetupQuality(), CACHE_TTL.getSeconds());

        } catch (Exception e) {
            log.warn("Failed to cache AI response: {}", e.getMessage());
            // Don't fail the request if caching fails
        }
    }

    private String generateCacheKey(MarketContext context, TradingViewWebhook webhook,
                                    IntradayContext intradayContext) {
        // Round prices to nearest 0.5% to group similar setups
        final var priceDouble = context.getCurrentPrice().doubleValue();
        final var priceKey = Math.round(priceDouble * 200.0) / 200.0;

        // Round OI change to nearest 1%
        final var oiKey = Math.round(context.getOiChangePercent());

        // Round confidence to nearest 10
        final var confKey = (intradayContext.getConfidenceScore() / 10) * 10;

        // Round alignment score if available
        final var htfKey = context.getHtfBias() != null ? context.getHtfBias() : "NONE";

        return String.format("%s:%s:%s:%s:%s:%s:%s:%d:%d",
                context.getSymbol(),
                webhook.getStrategy(),
                webhook.getEventType(),
                webhook.getDirection(),
                htfKey,
                context.getVolatility(),
                intradayContext.getOiPriceBehavior(),
                confKey,
                oiKey
        );
    }
}

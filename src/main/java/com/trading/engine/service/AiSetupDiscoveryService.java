package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.config.ScannerConfig;
import com.trading.engine.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-powered setup discovery service for autonomous market scanning.
 * Unlike AiAnalysisService (which assesses known setups from TradingView),
 * this service asks Claude to DISCOVER setups from raw market data.
 */
@Service
@Slf4j
public class AiSetupDiscoveryService {

    private final ChatClient sonnetClient;
    private final ChatClient haikuClient;
    private final ClaudeConfig claudeConfig;
    private final ScannerConfig scannerConfig;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AiSetupDiscoveryService(
            ChatClient chatClient,
            @Qualifier("haikuChatClient") ChatClient haikuChatClient,
            ClaudeConfig claudeConfig,
            ScannerConfig scannerConfig) {
        this.sonnetClient = chatClient;
        this.haikuClient = haikuChatClient;
        this.claudeConfig = claudeConfig;
        this.scannerConfig = scannerConfig;
    }

    /**
     * Discover trading setups from raw market data.
     * This is the core autonomous scanning capability - AI identifies setups without TradingView.
     */
    public DiscoveredSetup discoverSetup(final ScreenResult screen,
                                          final MarketContext marketContext,
                                          final IntradayContext intradayContext,
                                          final String session) {
        log.info("AI setup discovery for {} - Screen signals: {}, Interest: {}/100",
                screen.getSymbol(), screen.getSignals().size(), screen.getInterestScore());

        try {
            // Quick pre-filter with Haiku to save costs
            if (claudeConfig.isEnableHaikuPrefilter()) {
                final var worthAnalyzing = haikuPrescreen(screen, session);
                if (!worthAnalyzing) {
                    log.info("Haiku says {} not worth deeper analysis", screen.getSymbol());
                    return DiscoveredSetup.builder()
                            .setupFound(false)
                            .noSetupReason("Pre-filter: No actionable setup identified")
                            .build();
                }
            }

            // Full discovery with Sonnet
            return performDiscovery(screen, marketContext, intradayContext, session);

        } catch (final Exception e) {
            log.error("AI discovery failed for {}: {}", screen.getSymbol(), e.getMessage());
            return DiscoveredSetup.builder()
                    .setupFound(false)
                    .noSetupReason("AI analysis unavailable: " + e.getMessage())
                    .build();
        }
    }

    private boolean haikuPrescreen(final ScreenResult screen, final String session) {
        try {
            final var prompt = buildHaikuPrescreenPrompt(screen, session);

            final var response = haikuClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            final var answer = response.trim().toUpperCase();
            return answer.startsWith("YES");

        } catch (final Exception e) {
            log.warn("Haiku prescreen failed for {}, proceeding with Sonnet: {}", screen.getSymbol(), e.getMessage());
            return true; // On error, proceed with deeper analysis
        }
    }

    private DiscoveredSetup performDiscovery(final ScreenResult screen,
                                              final MarketContext marketContext,
                                              final IntradayContext intradayContext,
                                              final String session) throws Exception {
        final var marketSnapshot = buildMarketSnapshot(screen, marketContext, intradayContext, session);
        final var outputConverter = new BeanOutputConverter<>(DiscoveredSetup.class);

        final var prompt = buildDiscoveryPrompt(screen, marketSnapshot, session, outputConverter.getFormat());

        final var response = sonnetClient.prompt()
                .user(prompt)
                .call()
                .content();

        final var discovered = outputConverter.convert(response);
        validateDiscovery(discovered);

        log.info("AI discovery result for {}: setupFound={}, direction={}, strategy={}, quality={}",
                screen.getSymbol(),
                discovered.isSetupFound(),
                discovered.getDirection(),
                discovered.getStrategy(),
                discovered.getSetupQuality());

        return discovered;
    }

    private String buildHaikuPrescreenPrompt(final ScreenResult screen, final String session) {
        return "You are a crypto trading setup screener. Quick assessment only.\n\n" +
                "Symbol: " + screen.getSymbol() + "\n" +
                "Session: " + session + "\n" +
                "Price: " + screen.getCurrentPrice() + "\n" +
                "24h High: " + screen.getPreviousDayHigh() + " | Low: " + screen.getPreviousDayLow() + "\n" +
                "OI Change: " + screen.getOiChangePercent() + "%\n" +
                "Funding: " + screen.getFundingRate() + "\n" +
                "Volatility: " + screen.getVolatilityState() + "\n" +
                "Price Location: " + screen.getPriceLocation() + "\n" +
                "Signals: " + String.join(", ", screen.getSignals()) + "\n\n" +
                "Is there a potential intraday trading setup here? Consider:\n" +
                "- Liquidity sweeps (price at extremes with OI divergence)\n" +
                "- Reversal setups (RSI extremes, funding extremes, squeeze potential)\n" +
                "- Breakout setups (volatility contraction, OI buildup)\n" +
                "- Continuation setups (strong trend with pullback)\n\n" +
                "Respond with ONLY 'YES' or 'NO'. Nothing else.";
    }

    private String buildMarketSnapshot(final ScreenResult screen,
                                        final MarketContext marketContext,
                                        final IntradayContext intradayContext,
                                        final String session) throws Exception {
        final var ctx = objectMapper.createObjectNode();

        // Symbol and session
        ctx.put("symbol", screen.getSymbol());
        ctx.put("session", session);

        // Price data
        ctx.put("currentPrice", screen.getCurrentPrice());
        ctx.put("24hHigh", screen.getPreviousDayHigh());
        ctx.put("24hLow", screen.getPreviousDayLow());
        ctx.put("priceLocation", screen.getPriceLocation());
        if (screen.getDistanceToKeyLevel() != null) {
            ctx.put("distToKeyLevel", String.format("%.2f%%", screen.getDistanceToKeyLevel()));
        }

        // Derivatives data
        ctx.put("oiChange", screen.getOiChangePercent());
        ctx.put("fundingRate", screen.getFundingRate());
        ctx.put("volatility", screen.getVolatilityState());
        ctx.put("atr", screen.getAtr());
        ctx.put("takerRatio", screen.getTakerRatio());

        // Pre-screening signals
        final var signalsArray = objectMapper.createArrayNode();
        screen.getSignals().forEach(signalsArray::add);
        ctx.set("screeningSignals", signalsArray);

        // Intraday context
        if (intradayContext != null) {
            ctx.put("trend15m", intradayContext.getTrendBias15m());
            ctx.put("trend5m", intradayContext.getTrendBias5m());
            ctx.put("trend1m", intradayContext.getTrendBias1m());
            ctx.put("oiPriceBehavior", intradayContext.getOiPriceBehavior());
            ctx.put("volumeConfirmation", intradayContext.getVolumeConfirmation());
            ctx.put("sessionNarrative", intradayContext.getSessionNarrative());
            ctx.put("confidenceScore", intradayContext.getConfidenceScore());
            if (intradayContext.getFundingRateDelta() != null) {
                ctx.put("fundingDelta", intradayContext.getFundingRateDelta());
            }
            if (intradayContext.getOrderBookImbalance() != null) {
                ctx.put("orderBookImbalance", intradayContext.getOrderBookImbalance());
            }
        }

        // Market context extras
        if (marketContext != null) {
            ctx.put("htfBias", marketContext.getHtfBias());
        }

        ctx.put("trendBias", screen.getTrendBias());
        ctx.put("interestScore", screen.getInterestScore());

        return objectMapper.writeValueAsString(ctx);
    }

    private String buildDiscoveryPrompt(final ScreenResult screen,
                                         final String marketSnapshot,
                                         final String session,
                                         final String outputFormat) {
        return "You are an expert crypto derivatives trader analyzing LIVE market data from Binance Futures.\n" +
                "Your job is to DISCOVER if there is a tradeable intraday setup right now.\n\n" +
                "IMPORTANT RULES:\n" +
                "- You are looking at REAL-TIME data. Be decisive.\n" +
                "- Only identify setups with clear risk/reward (minimum 1:3 R:R)\n" +
                "- Be honest - if there's no setup, say so. Don't force trades.\n" +
                "- Consider the current session (" + session + ") for timing relevance\n\n" +
                "STRATEGY TOOLKIT - identify which (if any) applies:\n\n" +
                "1. LIQUIDITY_SWEEP: Price sweeps previous high/low, grabs liquidity, reverses.\n" +
                "   Signals: Price at/beyond 24h extremes, OI divergence, aggressive taker activity.\n" +
                "   Profile: 45-55% WR, 1:3-1:5 R:R\n\n" +
                "2. CANDLE_2_CLOSURE: Counter-trend reversal at extremes.\n" +
                "   Signals: Extreme funding, OI squeeze signals, price at range extremes.\n" +
                "   Profile: 60-70% WR, 1:2-1:3 R:R\n\n" +
                "3. BREAKOUT_RETEST: Volatility contraction breaking out.\n" +
                "   Signals: Contracting volatility, OI buildup, price compressing.\n" +
                "   Profile: 30-40% WR, 1:5-1:10 R:R\n\n" +
                "4. CONTINUATION: Trend pullback to key level.\n" +
                "   Signals: Strong trend, pullback to support/resistance, volume drying up on pullback.\n" +
                "   Profile: 50-60% WR, 1:2-1:4 R:R\n\n" +
                "LIVE MARKET DATA:\n" + marketSnapshot + "\n\n" +
                "ANALYSIS INSTRUCTIONS:\n" +
                "1. Assess if ANY of the 4 strategies have a valid setup right now\n" +
                "2. If setupFound=true: Provide direction, strategy, quality (A/B/C), entry/stop/targets\n" +
                "3. If setupFound=false: Explain why and what conditions would create a setup\n" +
                "4. Always provide watchCondition and keyLevelToWatch\n" +
                "5. For entry/stop/targets, use exact prices based on the data\n" +
                "6. setupQuality A = excellent confluence, B = good with minor concerns, C = weak/forced\n\n" +
                "Quality Guidelines:\n" +
                "- A: 3+ confluence factors, clear invalidation, favorable session timing\n" +
                "- B: 2 confluence factors, reasonable R:R but some concerns\n" +
                "- C: Weak setup, forced, or poor timing\n\n" +
                outputFormat;
    }

    private void validateDiscovery(final DiscoveredSetup setup) {
        if (setup == null) {
            throw new IllegalStateException("AI returned null discovery result");
        }

        if (setup.isSetupFound()) {
            // Validate required fields for a found setup
            if (setup.getDirection() == null || setup.getDirection().isEmpty()) {
                setup.setDirection("UNKNOWN");
            }
            if (setup.getStrategy() == null || setup.getStrategy().isEmpty()) {
                setup.setStrategy("UNKNOWN");
            }
            if (setup.getSetupQuality() == null || !List.of("A", "B", "C").contains(setup.getSetupQuality())) {
                setup.setSetupQuality("B"); // Default to B
            }
            if (setup.getAlignmentScore() == null) {
                setup.setAlignmentScore(getDefaultAlignmentScore(setup.getSetupQuality()));
            }
            // Normalize direction
            final var dir = setup.getDirection().toUpperCase();
            if (dir.contains("BULL") || dir.contains("LONG")) {
                setup.setDirection("LONG");
            } else if (dir.contains("BEAR") || dir.contains("SHORT")) {
                setup.setDirection("SHORT");
            }
        }
    }

    private int getDefaultAlignmentScore(final String quality) {
        return switch (quality) {
            case "A" -> 80;
            case "B" -> 60;
            case "C" -> 40;
            default -> 50;
        };
    }
}

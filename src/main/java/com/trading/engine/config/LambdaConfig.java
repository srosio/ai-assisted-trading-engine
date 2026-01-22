package com.trading.engine.config;

import com.trading.engine.domain.JournalEntry;
import com.trading.engine.domain.TradingViewWebhook;
import com.trading.engine.dto.JournalEntryResponse;
import com.trading.engine.dto.JournalStatistics;
import com.trading.engine.repository.JournalEntryRepository;
import com.trading.engine.service.IngressService;
import com.trading.engine.service.SignalProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LambdaConfig {

    private final IngressService ingressService;
    private final SignalProcessingService signalProcessor;
    private final JournalEntryRepository journalRepository;

    /**
     * Webhook Function: Receives TradingView webhook data
     * Corresponds to: POST /api/webhook/tradingview
     * Returns immediate response, processes synchronously (Lambda requirement)
     */
    @Bean
    public Function<TradingViewWebhook, Message<Map<String, Object>>> processWebhook() {
        return webhook -> {
            log.info("Received webhook - Symbol: {}, Strategy: {}, Event: {}",
                    webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType());

            // Step 1: Validate payload
            if (!ingressService.isValidPayload(webhook)) {
                return MessageBuilder.withPayload(Map.<String, Object>of(
                        "success", false,
                        "error", "Invalid payload: missing required fields"
                )).setHeader("statusCode", 400).build();
            }

            // Step 2: Check for duplicates
            if (ingressService.isDuplicate(webhook)) {
                log.info("Duplicate event ignored");
                return MessageBuilder.withPayload(Map.<String, Object>of(
                        "success", true,
                        "status", "DUPLICATE",
                        "message", "Event already processed"
                )).setHeader("statusCode", 200).build();
            }

            // Step 3: Align session
            final var alignedSession = ingressService.processIngress(webhook);
            webhook.setSession(alignedSession);

            // Step 4: Process synchronously (Lambda requires completion before return)
            // Start processing in separate thread but wait for completion
            final var startTime = System.currentTimeMillis();
            signalProcessor.processWebhook(webhook);
            final var duration = System.currentTimeMillis() - startTime;
            
            log.info("Webhook processed in {}ms", duration);

            // Return 202 Accepted (processing complete but async-style response)
            return MessageBuilder.withPayload(Map.<String, Object>of(
                    "success", true,
                    "status", "ACCEPTED",
                    "message", "Webhook received and processed",
                    "symbol", webhook.getSymbol(),
                    "strategy", webhook.getStrategy(),
                    "eventType", webhook.getEventType(),
                    "direction", webhook.getDirection(),
                    "processingTimeMs", duration
            )).setHeader("statusCode", 202).build();
        };
    }

    /**
     * Retrieve All Entries Function
     * Corresponds to: GET /api/journal (simplified)
     * Input: Map of query parameters (limit, etc.)
     */
    @Bean
    public Function<Map<String, String>, List<JournalEntryResponse>> getJournalEntries() {
        return params -> {
            // Simplified: DynamoDB Scan or Query. Pagination ignored on this pass.
            // In real world, we would use LastEvaluatedKey.
            List<JournalEntry> entries = journalRepository.findAll();

            // In-memory sort since findAll is a Scan
            entries.sort(Comparator.comparing(JournalEntry::getCreatedAt).reversed());

            return entries.stream()
                    .map(JournalEntryResponse::fromEntity)
                    .toList();
        };
    }

    /**
     * Get Statistics Function
     * Corresponds to: GET /api/journal/statistics
     */
    @Bean
    public Supplier<JournalStatistics> getStatistics() {
        return () -> {
            // Simplified: All time stats
            final var entries = journalRepository.findAll();
            return calculateStatistics(entries);
        };
    }

    // Helper method (copied from Controller)
    private JournalStatistics calculateStatistics(final List<JournalEntry> entries) {
        final var stats = new JournalStatistics();
        stats.setTotalSignals(entries.size());

        final var validCount = entries.stream().filter(e -> "VALID".equals(e.getStatus())).count();
        stats.setValidSignals((int) validCount);
        stats.setInvalidSignals(entries.size() - (int) validCount);

        final var qualityCounts = entries.stream()
                .filter(e -> e.getSetupQuality() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        JournalEntry::getSetupQuality, java.util.stream.Collectors.counting()));
        stats.setQualityACount(qualityCounts.getOrDefault("A", 0L).intValue());
        stats.setQualityBCount(qualityCounts.getOrDefault("B", 0L).intValue());
        stats.setQualityCCount(qualityCounts.getOrDefault("C", 0L).intValue());

        final var takenTrades = entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getTradeTaken()))
                .toList();
        stats.setTakenTrades(takenTrades.size());

        if (!takenTrades.isEmpty()) {
            final var wins = takenTrades.stream().filter(e -> "WIN".equals(e.getOutcome())).count();
            final var losses = takenTrades.stream().filter(e -> "LOSS".equals(e.getOutcome())).count();
            final var breakevens = takenTrades.stream().filter(e -> "BREAKEVEN".equals(e.getOutcome())).count();

            stats.setWins((int) wins);
            stats.setLosses((int) losses);
            stats.setBreakevens((int) breakevens);

            if (wins + losses > 0) {
                final var winRate = (double) wins / (wins + losses) * 100;
                stats.setWinRate(BigDecimal.valueOf(winRate).setScale(2, RoundingMode.HALF_UP));
            }

            final var totalPnl = takenTrades.stream()
                    .filter(e -> e.getPnl() != null)
                    .map(JournalEntry::getPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.setTotalPnl(totalPnl);

            final var avgPnl = totalPnl.divide(
                    BigDecimal.valueOf(takenTrades.size()), 2, RoundingMode.HALF_UP);
            stats.setAveragePnl(avgPnl);
        }

        // Direction stats
        final var longCount = entries.stream().filter(e -> "LONG".equals(e.getDirection())).count();
        stats.setLongSignals((int) longCount);
        stats.setShortSignals(entries.size() - (int) longCount);

        return stats;
    }
}

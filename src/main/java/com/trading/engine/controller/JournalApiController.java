package com.trading.engine.controller;

import com.trading.engine.domain.JournalEntry;
import com.trading.engine.dto.JournalEntryResponse;
import com.trading.engine.dto.JournalStatistics;
import com.trading.engine.dto.TradeOutcomeUpdate;
import com.trading.engine.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for querying trade journal data
 */
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
@Slf4j
public class JournalApiController {

    private final JournalEntryRepository journalRepository;

    /**
     * Get all journal entries with pagination
     */
    @GetMapping
    public ResponseEntity<Page<JournalEntryResponse>> getAllEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        final var sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        final var pageable = PageRequest.of(page, size, sort);

        final var entries = journalRepository.findAll(pageable);
        final var responsePage = entries.map(JournalEntryResponse::fromEntity);
        return ResponseEntity.ok(responsePage);
    }

    /**
     * Get journal entry by signal ID
     */
    @GetMapping("/{signalId}")
    public ResponseEntity<JournalEntryResponse> getBySignalId(@PathVariable String signalId) {
        return journalRepository.findBySignalId(signalId)
                .map(JournalEntryResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get journal entries by symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<JournalEntryResponse>> getBySymbol(@PathVariable String symbol) {
        final var entries = journalRepository.findBySymbolOrderByCreatedAtDesc(symbol);
        final var responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get journal entries by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<JournalEntryResponse>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        final var start = startDate.atStartOfDay();
        final var end = endDate.atTime(LocalTime.MAX);

        final var entries = journalRepository.findByCreatedAtBetween(start, end);
        final var responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get journal entries by setup quality
     */
    @GetMapping("/quality/{quality}")
    public ResponseEntity<List<JournalEntryResponse>> getBySetupQuality(@PathVariable String quality) {
        final var entries = journalRepository.findBySetupQuality(quality.toUpperCase());
        final var responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get today's journal entries
     */
    @GetMapping("/today")
    public ResponseEntity<List<JournalEntryResponse>> getTodayEntries() {
        final var startOfDay = LocalDate.now().atStartOfDay();
        final var endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        final var entries = journalRepository.findByCreatedAtBetween(startOfDay, endOfDay);
        final var responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get taken trades (trades that were actually executed)
     */
    @GetMapping("/taken")
    public ResponseEntity<List<JournalEntryResponse>> getTakenTrades() {
        final var startOfDay = LocalDate.now().atStartOfDay();
        final var entries = journalRepository.findTakenTradesToday(startOfDay);
        final var responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Update trade outcome (for manual tracking after trade execution)
     */
    @PutMapping("/{signalId}/outcome")
    public ResponseEntity<JournalEntryResponse> updateTradeOutcome(
            @PathVariable String signalId,
            @RequestBody TradeOutcomeUpdate outcomeUpdate) {

        return journalRepository.findBySignalId(signalId)
                .map(entry -> {
                    entry.setTradeTaken(outcomeUpdate.getTradeTaken());
                    if (outcomeUpdate.getExitPrice() != null) {
                        entry.setExitPrice(outcomeUpdate.getExitPrice());
                    }
                    if (outcomeUpdate.getOutcome() != null) {
                        entry.setOutcome(outcomeUpdate.getOutcome());
                    }
                    if (outcomeUpdate.getPnl() != null) {
                        entry.setPnl(outcomeUpdate.getPnl());
                    }
                    if (outcomeUpdate.getNotes() != null) {
                        entry.setNotes(outcomeUpdate.getNotes());
                    }
                    if (outcomeUpdate.getClosedAt() != null) {
                        entry.setClosedAt(outcomeUpdate.getClosedAt());
                    }

                    final var updated = journalRepository.save(entry);
                    log.info("Updated trade outcome for signal: {} - Outcome: {}", signalId, outcomeUpdate.getOutcome());
                    return ResponseEntity.ok(JournalEntryResponse.fromEntity(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get trading statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<JournalStatistics> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<JournalEntry> entries;

        if (startDate != null && endDate != null) {
            final var start = startDate.atStartOfDay();
            final var end = endDate.atTime(LocalTime.MAX);
            entries = journalRepository.findByCreatedAtBetween(start, end);
        } else {
            entries = journalRepository.findAll();
        }

        final var stats = calculateStatistics(entries);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get statistics by strategy
     */
    @GetMapping("/statistics/by-strategy")
    public ResponseEntity<Map<String, JournalStatistics>> getStatisticsByStrategy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<JournalEntry> entries;

        if (startDate != null && endDate != null) {
            final var start = startDate.atStartOfDay();
            final var end = endDate.atTime(LocalTime.MAX);
            entries = journalRepository.findByCreatedAtBetween(start, end);
        } else {
            entries = journalRepository.findAll();
        }

        final var statsByStrategy = new HashMap<String, JournalStatistics>();

        // Group by strategy (use new strategy field if available, otherwise extract from event)
        final var grouped = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getStrategy() != null ? entry.getStrategy() : extractStrategyFromEvent(entry.getEvent())
                ));

        grouped.forEach((strategy, strategyEntries) -> {
            statsByStrategy.put(strategy, calculateStatistics(strategyEntries));
        });

        return ResponseEntity.ok(statsByStrategy);
    }

    /**
     * Get statistics by symbol
     */
    @GetMapping("/statistics/by-symbol")
    public ResponseEntity<Map<String, JournalStatistics>> getStatisticsBySymbol() {
        final var entries = journalRepository.findAll();

        final var statsBySymbol = new HashMap<String, JournalStatistics>();

        final var grouped = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(JournalEntry::getSymbol));

        grouped.forEach((symbol, symbolEntries) -> {
            statsBySymbol.put(symbol, calculateStatistics(symbolEntries));
        });

        return ResponseEntity.ok(statsBySymbol);
    }

    /**
     * Delete journal entry
     */
    @DeleteMapping("/{signalId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable String signalId) {
        return journalRepository.findBySignalId(signalId)
                .map(entry -> {
                    journalRepository.delete(entry);
                    log.info("Deleted journal entry: {}", signalId);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Calculate statistics from journal entries
     */
    private JournalStatistics calculateStatistics(final List<JournalEntry> entries) {
        final var stats = new JournalStatistics();

        // Total signals
        stats.setTotalSignals(entries.size());

        // Valid vs Invalid
        final var validCount = entries.stream()
                .filter(e -> "VALID".equals(e.getStatus()))
                .count();
        stats.setValidSignals((int) validCount);
        stats.setInvalidSignals(entries.size() - (int) validCount);

        // By quality
        final var qualityCounts = entries.stream()
                .filter(e -> e.getSetupQuality() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        JournalEntry::getSetupQuality,
                        java.util.stream.Collectors.counting()
                ));
        stats.setQualityACount(qualityCounts.getOrDefault("A", 0L).intValue());
        stats.setQualityBCount(qualityCounts.getOrDefault("B", 0L).intValue());
        stats.setQualityCCount(qualityCounts.getOrDefault("C", 0L).intValue());

        // Taken trades
        final var takenTrades = entries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getTradeTaken()))
                .toList();
        stats.setTakenTrades(takenTrades.size());

        // Win/Loss statistics
        if (!takenTrades.isEmpty()) {
            final var wins = takenTrades.stream()
                    .filter(e -> "WIN".equals(e.getOutcome()))
                    .count();
            final var losses = takenTrades.stream()
                    .filter(e -> "LOSS".equals(e.getOutcome()))
                    .count();
            final var breakevens = takenTrades.stream()
                    .filter(e -> "BREAKEVEN".equals(e.getOutcome()))
                    .count();

            stats.setWins((int) wins);
            stats.setLosses((int) losses);
            stats.setBreakevens((int) breakevens);

            if (wins + losses > 0) {
                final var winRate = (double) wins / (wins + losses) * 100;
                stats.setWinRate(BigDecimal.valueOf(winRate).setScale(2, RoundingMode.HALF_UP));
            }

            // PnL
            final var totalPnl = takenTrades.stream()
                    .filter(e -> e.getPnl() != null)
                    .map(JournalEntry::getPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.setTotalPnl(totalPnl);

            if (!takenTrades.isEmpty()) {
                final var avgPnl = totalPnl.divide(
                        BigDecimal.valueOf(takenTrades.size()),
                        2,
                        RoundingMode.HALF_UP
                );
                stats.setAveragePnl(avgPnl);
            }
        }

        // By direction
        final var longCount = entries.stream()
                .filter(e -> "LONG".equals(e.getDirection()))
                .count();
        stats.setLongSignals((int) longCount);
        stats.setShortSignals(entries.size() - (int) longCount);

        return stats;
    }

    /**
     * Extract strategy name from event string
     */
    private String extractStrategyFromEvent(final String event) {
        if (event == null) {
            return "Unknown";
        }

        final var eventLower = event.toLowerCase();

        if (eventLower.contains("scalping") || eventLower.contains("bb_extreme")) {
            return "High Win Rate Scalping";
        } else if (eventLower.contains("swing") || eventLower.contains("pullback")) {
            return "Medium Win Rate Swing";
        } else if (eventLower.contains("breakout") || eventLower.contains("consolidation_break")) {
            return "Low Win Rate Breakout";
        } else if (eventLower.contains("adaptive") || eventLower.contains("regime")) {
            return "Adaptive Strategy";
        } else {
            return "Liquidity Sweep";
        }
    }
}

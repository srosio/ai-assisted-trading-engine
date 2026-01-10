package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.config.TradingConfig;
import com.trading.engine.domain.JournalEntry;
import com.trading.engine.domain.TradeSignal;
import com.trading.engine.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalEntryRepository journalRepository;
    private final TradingConfig tradingConfig;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional
    public JournalEntry createEntry(final TradeSignal signal) {
        log.info("Creating journal entry for signal: {}", signal.getSignalId());

        try {
            final var entry = JournalEntry.builder()
                    .signalId(signal.getSignalId())
                    .symbol(signal.getSymbol())
                    .direction(signal.getDirection())
                    .event(signal.getEvent())
                    .webhookPayload(objectMapper.writeValueAsString(signal))
                    .marketContext(objectMapper.writeValueAsString(signal.getMarketContext()))
                    .aiAssessment(objectMapper.writeValueAsString(signal.getAiAssessment()))
                    .ruleResult(objectMapper.writeValueAsString(signal.getRuleResult()))
                    .setupQuality(signal.getAiAssessment().getSetupQuality())
                    .rulesPassed(signal.getRuleResult().isPassed())
                    .session(signal.getMarketContext().getSession())
                    .status(signal.getStatus())
                    .build();

            // Risk calculations (entry, stop, size, etc.) are done manually by human trader
            // These fields remain null and can be filled in later via updateTradeOutcome

            final var saved = journalRepository.save(entry);
            log.info("Journal entry created with ID: {}", saved.getId());
            return saved;

        } catch (final Exception e) {
            log.error("Error creating journal entry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create journal entry", e);
        }
    }

    public long getTodayTradeCount() {
        final var startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        return journalRepository.countTradesToday(startOfDay);
    }

    public BigDecimal getTodayPnL() {
        final var startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        final var todayTrades = journalRepository.findTakenTradesToday(startOfDay);

        return todayTrades.stream()
                .filter(entry -> entry.getPnl() != null)
                .map(JournalEntry::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isDailyLossWithinLimit() {
        final var todayPnL = getTodayPnL();
        final var accountBalance = tradingConfig.getAccountBalance();
        final var riskPercent = tradingConfig.getDefaultRiskPercent();
        final var riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

        final var maxLoss = tradingConfig.getMaxDailyLossR()
                .multiply(riskAmount)
                .negate();

        return todayPnL.compareTo(maxLoss) >= 0;
    }

    @Transactional
    public void updateTradeOutcome(final String signalId, final boolean taken, final BigDecimal exitPrice,
                                     final BigDecimal pnl, final String notes, final String outcome) {
        journalRepository.findBySignalId(signalId).ifPresent(entry -> {
            entry.setTradeTaken(taken);
            entry.setExitPrice(exitPrice);
            entry.setPnl(pnl);
            entry.setNotes(notes);
            entry.setOutcome(outcome);
            entry.setClosedAt(LocalDateTime.now());
            journalRepository.save(entry);
            log.info("Updated journal entry {} with outcome: {}", signalId, outcome);
        });
    }

    public List<JournalEntry> getEntriesBySymbol(final String symbol) {
        return journalRepository.findBySymbolOrderByCreatedAtDesc(symbol);
    }

    public List<JournalEntry> getEntriesByDateRange(final LocalDateTime start, final LocalDateTime end) {
        return journalRepository.findByCreatedAtBetween(start, end);
    }

    public List<JournalEntry> getEntriesByQuality(final String quality) {
        return journalRepository.findBySetupQuality(quality);
    }

    public PerformanceStats getPerformanceStats(final LocalDateTime start, final LocalDateTime end) {
        final var entries = journalRepository.findByCreatedAtBetween(start, end);

        final var totalSignals = entries.size();
        final var tradesTaken = entries.stream().filter(e -> Boolean.TRUE.equals(e.getTradeTaken())).count();
        final var wins = entries.stream().filter(e -> "WIN".equals(e.getOutcome())).count();
        final var losses = entries.stream().filter(e -> "LOSS".equals(e.getOutcome())).count();

        final var totalPnL = entries.stream()
                .filter(e -> e.getPnl() != null)
                .map(JournalEntry::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final var winRate = tradesTaken > 0 ? (double) wins / tradesTaken * 100 : 0;

        return new PerformanceStats(totalSignals, tradesTaken, wins, losses, totalPnL, winRate);
    }

    public record PerformanceStats(
            long totalSignals,
            long tradesTaken,
            long wins,
            long losses,
            BigDecimal totalPnL,
            double winRate
    ) {}
}

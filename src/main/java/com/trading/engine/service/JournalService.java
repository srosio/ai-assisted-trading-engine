package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Journal Service for automatic trade logging and analysis.
 * Every signal is logged for future review and performance analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalEntryRepository journalRepository;
    private final TradingConfig tradingConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create journal entry from trade signal
     */
    @Transactional
    public JournalEntry createEntry(TradeSignal signal) {
        log.info("Creating journal entry for signal: {}", signal.getSignalId());

        try {
            JournalEntry entry = JournalEntry.builder()
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

            // Add risk parameters if available
            if (signal.getRiskCalculation() != null) {
                entry.setEntryPrice(signal.getRiskCalculation().getEntryPrice());
                entry.setStopLoss(signal.getRiskCalculation().getStopLoss());
                entry.setTakeProfit(signal.getRiskCalculation().getTakeProfit());
                entry.setPositionSize(signal.getRiskCalculation().getPositionSize());
                entry.setRiskRewardRatio(signal.getRiskCalculation().getRiskRewardRatio());
            }

            JournalEntry saved = journalRepository.save(entry);
            log.info("Journal entry created with ID: {}", saved.getId());
            return saved;

        } catch (Exception e) {
            log.error("Error creating journal entry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create journal entry", e);
        }
    }

    /**
     * Get count of trades today
     */
    public long getTodayTradeCount() {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        return journalRepository.countTradesToday(startOfDay);
    }

    /**
     * Get today's P&L (from closed trades)
     */
    public BigDecimal getTodayPnL() {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        List<JournalEntry> todayTrades = journalRepository.findTakenTradesToday(startOfDay);

        return todayTrades.stream()
                .filter(entry -> entry.getPnl() != null)
                .map(JournalEntry::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Check if daily loss limit has been exceeded
     */
    public boolean isDailyLossWithinLimit() {
        BigDecimal todayPnL = getTodayPnL();
        BigDecimal accountBalance = tradingConfig.getAccountBalance();
        BigDecimal riskPercent = tradingConfig.getDefaultRiskPercent();
        BigDecimal riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

        BigDecimal maxLoss = tradingConfig.getMaxDailyLossR()
                .multiply(riskAmount)
                .negate();

        return todayPnL.compareTo(maxLoss) >= 0;
    }

    /**
     * Update journal entry with trade outcome
     */
    @Transactional
    public void updateTradeOutcome(String signalId, boolean taken, BigDecimal exitPrice,
                                     BigDecimal pnl, String notes, String outcome) {
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

    /**
     * Get entries by symbol
     */
    public List<JournalEntry> getEntriesBySymbol(String symbol) {
        return journalRepository.findBySymbolOrderByCreatedAtDesc(symbol);
    }

    /**
     * Get entries in date range
     */
    public List<JournalEntry> getEntriesByDateRange(LocalDateTime start, LocalDateTime end) {
        return journalRepository.findByCreatedAtBetween(start, end);
    }

    /**
     * Get entries by setup quality
     */
    public List<JournalEntry> getEntriesByQuality(String quality) {
        return journalRepository.findBySetupQuality(quality);
    }

    /**
     * Get performance statistics
     */
    public PerformanceStats getPerformanceStats(LocalDateTime start, LocalDateTime end) {
        List<JournalEntry> entries = journalRepository.findByCreatedAtBetween(start, end);

        long totalSignals = entries.size();
        long tradesTaken = entries.stream().filter(e -> Boolean.TRUE.equals(e.getTradeTaken())).count();
        long wins = entries.stream().filter(e -> "WIN".equals(e.getOutcome())).count();
        long losses = entries.stream().filter(e -> "LOSS".equals(e.getOutcome())).count();

        BigDecimal totalPnL = entries.stream()
                .filter(e -> e.getPnl() != null)
                .map(JournalEntry::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double winRate = tradesTaken > 0 ? (double) wins / tradesTaken * 100 : 0;

        return new PerformanceStats(totalSignals, tradesTaken, wins, losses, totalPnL, winRate);
    }

    /**
     * Performance statistics record
     */
    public record PerformanceStats(
            long totalSignals,
            long tradesTaken,
            long wins,
            long losses,
            BigDecimal totalPnL,
            double winRate
    ) {}
}

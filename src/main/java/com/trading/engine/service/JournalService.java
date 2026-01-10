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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalEntryRepository journalRepository;
    private final TradingConfig tradingConfig;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional
    public void createEntry(final TradeSignal signal) {
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
                .map(JournalEntry::getPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isDailyLossWithinLimit() {
        final var todayPnL = getTodayPnL();
        final var accountBalance = tradingConfig.getAccountBalance();
        final var riskPercent = tradingConfig.getDefaultRiskPercent();
        final var riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        final var maxLoss = tradingConfig.getMaxDailyLossR()
                .multiply(riskAmount)
                .negate();

        return todayPnL.compareTo(maxLoss) >= 0;
    }
    
}

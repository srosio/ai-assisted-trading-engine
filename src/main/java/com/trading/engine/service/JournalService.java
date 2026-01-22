package com.trading.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.engine.domain.JournalEntry;
import com.trading.engine.domain.TradeSignal;
import com.trading.engine.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalEntryRepository journalRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public void createEntry(final TradeSignal signal) {
        log.info("Creating journal entry for signal: {}", signal.getSignalId());

        try {
            final var entry = JournalEntry.builder()
                    .signalId(signal.getSignalId())
                    .symbol(signal.getSymbol())
                    .direction(signal.getDirection())
                    .event(signal.getEvent())
                    .strategy(signal.getStrategy())
                    .eventType(signal.getEventType())
                    .webhookPayload(objectMapper.writeValueAsString(signal))
                    .marketContext(objectMapper.writeValueAsString(signal.getMarketContext()))
                    .aiAssessment(objectMapper.writeValueAsString(signal.getAiAssessment()))
                    .ruleResult(objectMapper.writeValueAsString(signal.getRuleResult()))
                    .setupQuality(signal.getAiAssessment().getSetupQuality())
                    .rulesPassed(signal.getRuleResult().isPassed())
                    .session(signal.getMarketContext().getSession())
                    .status(signal.getStatus())
                    .createdAt(LocalDateTime.now()) // Ensure createdAt is set
                    .build();

            // Set optional fields from execution plan if available
            if (signal.getExecutionPlan() != null) {
                if (signal.getExecutionPlan().getSuggestedStopPrice() != null) {
                    entry.setSuggestedStopLoss(signal.getExecutionPlan().getSuggestedStopPrice());
                }
            }

            final var saved = journalRepository.save(entry);
            log.info("Journal entry created with Signal ID: {}", saved.getSignalId());

        } catch (final Exception e) {
            log.error("Error creating journal entry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create journal entry", e);
        }
    }

}

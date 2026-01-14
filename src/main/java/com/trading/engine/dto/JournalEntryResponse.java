package com.trading.engine.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.engine.domain.JournalEntry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for journal entry API responses with parsed JSON fields
 */
@Data
@Slf4j
public class JournalEntryResponse {

    private Long id;
    private String signalId;
    private String symbol;
    private String direction;
    private String event;
    private String strategy;
    private String eventType;
    private BigDecimal sweptLevel;
    private BigDecimal suggestedStopLoss;

    @JsonRawValue
    private String marketContext;

    @JsonRawValue
    private String aiAssessment;

    @JsonRawValue
    private String ruleResult;

    private String setupQuality;
    private Boolean rulesPassed;
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private BigDecimal positionSize;
    private BigDecimal riskRewardRatio;
    private LocalDateTime createdAt;
    private String session;
    private String status;
    private Boolean tradeTaken;
    private BigDecimal exitPrice;
    private BigDecimal pnl;
    private String notes;
    private String outcome;
    private LocalDateTime closedAt;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert JournalEntry entity to response DTO
     */
    public static JournalEntryResponse fromEntity(JournalEntry entry) {
        final var response = new JournalEntryResponse();

        response.setId(entry.getId());
        response.setSignalId(entry.getSignalId());
        response.setSymbol(entry.getSymbol());
        response.setDirection(entry.getDirection());
        response.setEvent(entry.getEvent());
        response.setStrategy(entry.getStrategy());
        response.setEventType(entry.getEventType());
        response.setSweptLevel(entry.getSweptLevel());
        response.setSuggestedStopLoss(entry.getSuggestedStopLoss());

        // Parse JSON strings - return as-is for @JsonRawValue to handle
        response.setMarketContext(entry.getMarketContext());
        response.setAiAssessment(entry.getAiAssessment());
        response.setRuleResult(entry.getRuleResult());

        response.setSetupQuality(entry.getSetupQuality());
        response.setRulesPassed(entry.getRulesPassed());
        response.setEntryPrice(entry.getEntryPrice());
        response.setStopLoss(entry.getStopLoss());
        response.setTakeProfit(entry.getTakeProfit());
        response.setPositionSize(entry.getPositionSize());
        response.setRiskRewardRatio(entry.getRiskRewardRatio());
        response.setCreatedAt(entry.getCreatedAt());
        response.setSession(entry.getSession());
        response.setStatus(entry.getStatus());
        response.setTradeTaken(entry.getTradeTaken());
        response.setExitPrice(entry.getExitPrice());
        response.setPnl(entry.getPnl());
        response.setNotes(entry.getNotes());
        response.setOutcome(entry.getOutcome());
        response.setClosedAt(entry.getClosedAt());

        return response;
    }
}

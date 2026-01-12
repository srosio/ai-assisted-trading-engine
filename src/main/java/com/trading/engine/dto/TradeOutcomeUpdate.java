package com.trading.engine.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeOutcomeUpdate {

    private Boolean tradeTaken;
    private BigDecimal exitPrice;
    private String outcome;
    private BigDecimal pnl;
    private String notes;
    private LocalDateTime closedAt;
}

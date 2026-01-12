package com.trading.engine.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class JournalStatistics {

    private Integer totalSignals;
    private Integer validSignals;
    private Integer invalidSignals;

    private Integer qualityACount;
    private Integer qualityBCount;
    private Integer qualityCCount;

    private Integer takenTrades;
    private Integer wins;
    private Integer losses;
    private Integer breakevens;

    private BigDecimal winRate;
    private BigDecimal totalPnl;
    private BigDecimal averagePnl;

    private Integer longSignals;
    private Integer shortSignals;
}

package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@DynamoDbBean
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    // Partition Key: signalId
    private String signalId;

    // Attributes
    private String symbol;
    private String direction;
    private String event;
    private String strategy;
    private String eventType;
    private BigDecimal sweptLevel;
    private BigDecimal suggestedStopLoss;
    private String webhookPayload;
    private String marketContext;
    private String aiAssessment;
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

    // Derived fields/Indexes
    // We might want a GSI on createdAt for time-range queries, or Symbol

    @DynamoDbPartitionKey
    public String getSignalId() {
        return signalId;
    }

    // GSI: by-symbol (PK=symbol, SK=createdAt)
    @DynamoDbSecondaryPartitionKey(indexNames = "by-symbol")
    public String getSymbol() {
        return symbol;
    }

    @DynamoDbSecondarySortKey(indexNames = { "by-symbol", "by-quality" })
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // GSI: by-quality (PK=setupQuality, SK=createdAt)
    @DynamoDbSecondaryPartitionKey(indexNames = "by-quality")
    public String getSetupQuality() {
        return setupQuality;
    }
}

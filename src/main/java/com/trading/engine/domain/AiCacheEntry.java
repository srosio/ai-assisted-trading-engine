package com.trading.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class AiCacheEntry {

    private String cacheKey;
    private String response;  // JSON serialized CombinedAiAnalysis
    private String createdAt;
    private Long ttl;  // Unix epoch seconds for TTL
    private String symbol;
    private String strategy;
    private String quality;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("cacheKey")
    public String getCacheKey() {
        return cacheKey;
    }

    @DynamoDbAttribute("response")
    public String getResponse() {
        return response;
    }

    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return ttl;
    }

    @DynamoDbAttribute("symbol")
    public String getSymbol() {
        return symbol;
    }

    @DynamoDbAttribute("strategy")
    public String getStrategy() {
        return strategy;
    }

    @DynamoDbAttribute("quality")
    public String getQuality() {
        return quality;
    }
}

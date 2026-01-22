package com.trading.engine.repository;

import com.trading.engine.domain.AiCacheEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Repository
@Slf4j
public class AiCacheRepository implements InitializingBean {

    private final DynamoDbClient dynamoDbClient;
    private DynamoDbTable<AiCacheEntry> table;

    @Value("${dynamodb.cache-table-name:ai_cache}")
    private String cacheTableName;

    public AiCacheRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void afterPropertiesSet() {
        final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        this.table = enhancedClient.table(cacheTableName, TableSchema.fromBean(AiCacheEntry.class));
        log.info("Initialized AI cache repository with table: {}", cacheTableName);
    }

    public AiCacheEntry get(String cacheKey) {
        try {
            final Key key = Key.builder()
                    .partitionValue(cacheKey)
                    .build();

            return table.getItem(key);
        } catch (Exception e) {
            log.warn("Failed to get cache entry: {}", e.getMessage());
            return null;
        }
    }

    public void put(AiCacheEntry entry) {
        try {
            table.putItem(entry);
            log.debug("Cached AI response: key={}, quality={}, symbol={}",
                    entry.getCacheKey(), entry.getQuality(), entry.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to cache AI response: {}", e.getMessage());
            // Don't fail the request if caching fails
        }
    }

    public void delete(String cacheKey) {
        try {
            final Key key = Key.builder()
                    .partitionValue(cacheKey)
                    .build();

            table.deleteItem(key);
        } catch (Exception e) {
            log.warn("Failed to delete cache entry: {}", e.getMessage());
        }
    }

}

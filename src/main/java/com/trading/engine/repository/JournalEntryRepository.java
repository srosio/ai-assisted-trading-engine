package com.trading.engine.repository;

import com.trading.engine.domain.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Repository
@Slf4j
public class JournalEntryRepository implements InitializingBean {

    private DynamoDbTable<JournalEntry> table;

    @Value("${dynamodb.table-name:journal_entries}")
    private String tableName;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Override
    public void afterPropertiesSet() {
        // Initialize DynamoDB Client (Lazy init handled by Spring if configured right,
        // but here we do manual)
        // For SnapStart, the client should be created in constructor or init.
        // We use DefaultCredentialsProvider which checks Env Vars/Instance Profile
        DynamoDbClient ddb = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddb)
                .build();

        this.table = enhancedClient.table(tableName, TableSchema.fromBean(JournalEntry.class));
    }

    public JournalEntry save(JournalEntry entry) {
        table.putItem(entry);
        return entry;
    }

    public Optional<JournalEntry> findBySignalId(String signalId) {
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(signalId))));
    }

    public List<JournalEntry> findAll() {
        // Scan operation - expensive, use with care.
        return StreamSupport.stream(table.scan().items().spliterator(), false)
                .collect(Collectors.toList());
    }

    public List<JournalEntry> findBySymbol(String symbol) {
        // Query GSI 'by-symbol'
        return StreamSupport.stream(
                table.index("by-symbol")
                        .query(QueryConditional.keyEqualTo(k -> k.partitionValue(symbol)))
                        .stream().spliterator(),
                false)
                .map(page -> page.items())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public void delete(JournalEntry entry) {
        table.deleteItem(entry);
    }

    // Fallback for complex queries - simpler to do in memory for small datasets
    // or requires complex GSI design not fully implemented here.
}

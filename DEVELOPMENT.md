# Development Guide

Complete guide for developers working on the AI-Assisted Trading Engine.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [Development Workflow](#development-workflow)
- [Testing](#testing)
- [Code Style](#code-style)
- [Common Development Tasks](#common-development-tasks)
- [Debugging](#debugging)

---

## Development Environment Setup

### Prerequisites

- **Java 21 JDK** (OpenJDK or Oracle)
- **IDE**: IntelliJ IDEA (recommended) or Eclipse
- **Docker & Docker Compose** for local services
- **Git** for version control
- **Postman** or **curl** for API testing

### IDE Setup

#### IntelliJ IDEA (Recommended)

1. **Import Project**
   - File → Open → Select `build.gradle`
   - Import as Gradle project

2. **Configure JDK**
   - File → Project Structure → Project SDK → Select Java 21

3. **Install Plugins**
   - Lombok Plugin (required)
   - Spring Boot Assistant
   - Database Tools and SQL
   - GitToolBox

4. **Enable Annotation Processing**
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"

5. **Code Style**
   - Settings → Editor → Code Style → Java
   - Import from: `config/intellij-code-style.xml` (if available)

#### Eclipse

1. **Import Project**
   - File → Import → Gradle → Existing Gradle Project

2. **Install Lombok**
   - Download lombok.jar from https://projectlombok.org/
   - Run: `java -jar lombok.jar`
   - Select Eclipse installation directory

3. **Configure JDK**
   - Project → Properties → Java Build Path → Libraries → Add JDK 21

### Local Development Setup

```bash
# 1. Clone repository
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Create .env file
cp .env.example .env
nano .env  # Configure for development

# 3. Start local services
docker-compose up -d postgres redis

# 4. Build project
./gradlew build

# 5. Run application
./scripts/run.sh

# Or run from IDE:
# Run → Edit Configurations → Add New → Spring Boot
# Main class: com.trading.engine.TradingEngineApplication
# Environment variables: Load from .env
```

### Mock Mode Development

For rapid development without external API dependencies:

```bash
# Set in .env
SPRING_PROFILES_ACTIVE=mock

# Or run with profile
./gradlew bootRun --args='--spring.profiles.active=mock'
```

Mock mode provides:
- Fake API responses for Claude, Binance, Telegram
- No rate limiting
- Faster development cycle
- No API key costs

---

## Project Structure

```
ai-assisted-trading-engine/
├── src/
│   ├── main/
│   │   ├── java/com/trading/engine/
│   │   │   ├── config/              # Configuration classes
│   │   │   │   ├── SpringAiConfig.java
│   │   │   │   ├── BinanceConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── BinanceRateLimitFilter.java
│   │   │   ├── controller/          # REST controllers
│   │   │   │   ├── WebhookController.java
│   │   │   │   └── JournalApiController.java
│   │   │   ├── service/             # Business logic
│   │   │   │   ├── SignalProcessingService.java
│   │   │   │   ├── RuleEngineService.java
│   │   │   │   ├── AiAnalysisService.java
│   │   │   │   └── ExecutionAdvisoryService.java
│   │   │   ├── model/               # Domain models
│   │   │   ├── dto/                 # Data transfer objects
│   │   │   ├── repository/          # JPA repositories
│   │   │   └── TradingEngineApplication.java
│   │   └── resources/
│   │       ├── application.yml      # Application configuration
│   │       └── db/migration/        # Flyway database migrations
│   └── test/
│       └── java/com/trading/engine/ # Test classes
├── docs/                            # Documentation
│   ├── API_ENDPOINTS.md
│   └── tradingview/                 # TradingView Pine Scripts
├── scripts/                         # Deployment scripts
├── build.gradle                     # Gradle build configuration
├── docker-compose.yml               # Docker services
├── SETUP.md                         # Setup guide
├── DEVELOPMENT.md                   # This file
└── README.md                        # Project overview
```

---

## Architecture Overview

### System Architecture

```
┌─────────────────┐
│  TradingView    │  Webhook signals
│   (Pine Script) │─────────┐
└─────────────────┘         │
                            ▼
                    ┌───────────────────┐
                    │ WebhookController │
                    │   (API Gateway)   │
                    └────────┬──────────┘
                             │
                    ┌────────▼──────────┐
                    │  SignalProcessing │
                    │      Service      │
                    └────────┬──────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
   ┌────▼─────┐      ┌──────▼──────┐      ┌─────▼─────┐
   │   Rule   │      │     AI      │      │ Execution │
   │  Engine  │      │  Analysis   │      │ Advisory  │
   │  Service │      │   Service   │      │  Service  │
   └────┬─────┘      └──────┬──────┘      └─────┬─────┘
        │                   │                    │
        │            ┌──────▼──────┐             │
        │            │   Claude    │             │
        │            │     AI      │             │
        │            └─────────────┘             │
        │                                        │
        └────────────────┬───────────────────────┘
                         │
                  ┌──────▼──────┐
                  │   Journal   │
                  │ Entry Repo  │
                  └──────┬──────┘
                         │
                  ┌──────▼──────┐
                  │  PostgreSQL │
                  └─────────────┘
```

### Key Components

#### 1. **Controllers** (`controller/`)

- **WebhookController**: Handles TradingView webhook signals
- **JournalApiController**: Provides journal query and update endpoints

#### 2. **Services** (`service/`)

- **SignalProcessingService**: Orchestrates signal processing flow
- **RuleEngineService**: Enforces trading rules (risk, session, quality)
- **AiAnalysisService**: Queries Claude AI for market context
- **ExecutionAdvisoryService**: Provides position sizing and execution guidance

#### 3. **Configuration** (`config/`)

- **SpringAiConfig**: Claude AI client configuration
- **BinanceConfig**: Binance API client setup
- **SecurityConfig**: API key authentication
- **BinanceRateLimitFilter**: Rate limit enforcement and back-off

#### 4. **Data Layer**

- **JPA Entities**: `JournalEntry` domain model
- **Repositories**: Spring Data JPA repositories
- **Flyway Migrations**: Version-controlled schema changes

---

## Development Workflow

### 1. Feature Development

```bash
# 1. Create feature branch
git checkout -b feature/your-feature-name

# 2. Make changes
# Edit code in your IDE

# 3. Build and test
./gradlew build

# 4. Run locally
./scripts/run.sh

# 5. Test API
curl http://localhost:8080/api/webhook/health

# 6. Commit changes
git add .
git commit -m "feat: add your feature description"

# 7. Push and create PR
git push origin feature/your-feature-name
```

### 2. Hot Reload Development

#### Using Spring Boot DevTools

```gradle
// Add to build.gradle dependencies
developmentOnly 'org.springframework.boot:spring-boot-devtools'
```

Then run from IDE. Changes will auto-reload.

#### Using Gradle Continuous Build

```bash
# Terminal 1: Continuous compilation
./gradlew build --continuous

# Terminal 2: Run application
./scripts/run.sh
```

### 3. Database Changes

#### Creating a Migration

```bash
# 1. Create new migration file
touch src/main/resources/db/migration/V2__your_migration_description.sql

# 2. Write SQL
# Example:
ALTER TABLE journal_entries ADD COLUMN new_field VARCHAR(255);

# 3. Restart application (Flyway runs automatically)
./scripts/run.sh
```

#### Rollback Strategy

Flyway doesn't support automatic rollback. For rollback:

```sql
-- Create undo migration
-- V3__undo_your_migration.sql
ALTER TABLE journal_entries DROP COLUMN new_field;
```

### 4. Adding New API Endpoints

```java
// 1. Create DTO
@Data
public class NewFeatureRequest {
    private String parameter;
}

// 2. Add controller method
@PostMapping("/api/new-endpoint")
public ResponseEntity<?> newEndpoint(@RequestBody NewFeatureRequest request) {
    // Implementation
    return ResponseEntity.ok(result);
}

// 3. Update API_ENDPOINTS.md documentation

// 4. Write integration test
@Test
void testNewEndpoint() {
    // Test implementation
}
```

---

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests WebhookControllerTest

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Test Structure

```
src/test/java/com/trading/engine/
├── controller/          # Controller integration tests
├── service/             # Service unit tests
├── repository/          # Repository tests
└── integration/         # Full integration tests
```

### Writing Tests

#### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    @Mock
    private TradingConfig tradingConfig;

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @Test
    void shouldBlockTradeWhenRiskTooHigh() {
        // Given
        when(tradingConfig.getMaxRiskPercent()).thenReturn(1.0);
        TradeSignal signal = createSignalWithRisk(2.0);

        // When
        RuleCheckResult result = ruleEngineService.checkRules(signal);

        // Then
        assertFalse(result.isPassed());
        assertThat(result.getViolations()).contains("Risk too high");
    }
}
```

#### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldProcessValidWebhook() throws Exception {
        mockMvc.perform(post("/api/webhook/tradingview")
                .header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validWebhookJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

### Test Coverage Goals

- **Unit Tests**: 80%+ coverage for service layer
- **Integration Tests**: All API endpoints
- **Edge Cases**: Error handling, rate limiting, validation

---

## Code Style

### General Guidelines

- **Follow Java conventions**: CamelCase for classes, camelCase for methods
- **Use Lombok**: Avoid boilerplate (`@Data`, `@Builder`, `@Slf4j`)
- **Immutability**: Prefer immutable DTOs where possible
- **Clear naming**: Methods should describe what they do

### Example: Good Code Style

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SignalProcessingService {

    private final RuleEngineService ruleEngine;
    private final AiAnalysisService aiAnalysis;
    private final JournalEntryRepository journalRepo;

    public ProcessedSignal processSignal(WebhookSignal webhook) {
        log.info("Processing signal for {} on {}", webhook.getSymbol(), webhook.getEvent());

        // Validate signal
        validateWebhook(webhook);

        // Check trading rules
        RuleCheckResult ruleCheck = ruleEngine.checkRules(webhook);
        if (!ruleCheck.isPassed()) {
            log.warn("Signal failed rules: {}", ruleCheck.getViolations());
            return ProcessedSignal.invalid(ruleCheck.getViolations());
        }

        // Get AI analysis
        String aiContext = aiAnalysis.analyzeMarketContext(webhook);

        // Save to journal
        JournalEntry entry = journalRepo.save(createJournalEntry(webhook, aiContext));

        return ProcessedSignal.valid(entry);
    }

    private void validateWebhook(WebhookSignal webhook) {
        Objects.requireNonNull(webhook.getSymbol(), "Symbol is required");
        Objects.requireNonNull(webhook.getEvent(), "Event is required");
    }

    private JournalEntry createJournalEntry(WebhookSignal webhook, String aiContext) {
        return JournalEntry.builder()
            .symbol(webhook.getSymbol())
            .event(webhook.getEvent())
            .aiContext(aiContext)
            .timestamp(Instant.now())
            .build();
    }
}
```

### Code Formatting

```bash
# Format code (if using spotless)
./gradlew spotlessApply

# Check formatting
./gradlew spotlessCheck
```

---

## Common Development Tasks

### Adding a New Trading Rule

1. **Update `TradingConfig`**

```java
@ConfigurationProperties(prefix = "trading")
@Data
public class TradingConfig {
    // Add new rule property
    private boolean myNewRuleEnabled = true;
}
```

2. **Update `application.yml`**

```yaml
trading:
  my-new-rule-enabled: true
```

3. **Implement in `RuleEngineService`**

```java
public RuleCheckResult checkMyNewRule(TradeSignal signal) {
    if (!tradingConfig.isMyNewRuleEnabled()) {
        return RuleCheckResult.skipped("Rule disabled");
    }

    boolean passed = evaluateRule(signal);
    return passed
        ? RuleCheckResult.passed()
        : RuleCheckResult.failed("Rule violation message");
}
```

4. **Add tests**

```java
@Test
void shouldEnforceMyNewRule() {
    // Test implementation
}
```

### Adding External API Integration

```java
@Configuration
public class NewApiConfig {

    @Value("${new-api.key}")
    private String apiKey;

    @Bean
    public WebClient newApiClient() {
        return WebClient.builder()
            .baseUrl("https://api.example.com")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class NewApiService {

    private final WebClient newApiClient;

    public ApiResponse callApi(RequestData data) {
        return newApiClient.post()
            .uri("/endpoint")
            .bodyValue(data)
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .block();
    }
}
```

### Adding Cache

```java
@Service
@CacheConfig(cacheNames = "myCache")
public class MyCachedService {

    @Cacheable(key = "#symbol")
    public Data getExpensiveData(String symbol) {
        // Expensive operation
    }

    @CacheEvict(allEntries = true)
    public void clearCache() {
        // Cache cleared
    }
}
```

---

## Debugging

### Enable Debug Logging

```yaml
# application.yml
logging:
  level:
    com.trading.engine: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

Or via environment:

```bash
LOGGING_LEVEL_COM_TRADING_ENGINE=DEBUG ./scripts/run.sh
```

### IntelliJ Debugger

1. Set breakpoints in code
2. Run → Debug 'TradingEngineApplication'
3. Use evaluation tools to inspect variables

### Remote Debugging

```bash
# Start application with debug port
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  ./scripts/run.sh

# In IntelliJ:
# Run → Edit Configurations → Add New → Remote JVM Debug
# Host: localhost, Port: 5005
```

### Database Debugging

```bash
# Connect to database
docker exec -it trading_engine_db psql -U postgres -d trading_engine

# View recent journal entries
SELECT * FROM journal_entries ORDER BY created_at DESC LIMIT 10;

# Check specific signal
SELECT * FROM journal_entries WHERE signal_id = 'uuid-here';
```

### API Debugging with curl

```bash
# Test with verbose output
curl -v -X POST http://localhost:8080/api/webhook/tradingview \
  -H "X-API-Key: your-key" \
  -H "Content-Type: application/json" \
  -d @test-signal.json

# Save response
curl -X POST ... > response.json

# Time request
time curl -X POST ...
```

### Common Issues

**Issue**: "Port 8080 already in use"

```bash
# Find and kill process
sudo lsof -i :8080
sudo kill -9 <PID>
```

**Issue**: "Bean creation error"

- Check `@Configuration` classes
- Verify `application.yml` syntax
- Check for circular dependencies

**Issue**: "Database connection failed"

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Test connection
docker exec -it trading_engine_db psql -U postgres -c '\l'
```

---

## Performance Optimization

### Profiling

```bash
# Run with JFR (Java Flight Recorder)
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
  -jar build/libs/ai-assisted-trading-engine-1.0.0.jar

# Analyze recording
# Use JDK Mission Control or IntelliJ Profiler
```

### Database Query Optimization

```yaml
# Enable SQL logging
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
```

Then analyze slow queries and add indexes:

```sql
CREATE INDEX idx_journal_symbol_date
ON journal_entries(symbol, created_at DESC);
```

---

## Contributing

### Pull Request Process

1. Create feature branch from `main`
2. Make changes with tests
3. Run `./gradlew build` successfully
4. Update documentation
5. Submit PR with description
6. Address review feedback
7. Squash and merge

### Commit Message Format

```
type(scope): subject

body

footer
```

**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Example**:

```
feat(rules): add volatility filter rule

Implement volatility checking using ATR indicator
to filter high-volatility setups that may be risky.

Closes #123
```

---

## Additional Resources

- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **Spring AI Docs**: https://docs.spring.io/spring-ai/reference/
- **Lombok**: https://projectlombok.org/
- **Flyway**: https://flywaydb.org/documentation/

---

**Happy Coding! 💻**

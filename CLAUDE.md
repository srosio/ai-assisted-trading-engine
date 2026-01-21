# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI-Assisted Crypto Trading Engine - A serverless, rule-based crypto trading system that uses Claude AI exclusively for market context analysis (not trade decisions). The system processes TradingView webhook signals, validates them against strict trading rules, and maintains a comprehensive trade journal.

**Core Philosophy**: Strict, non-negotiable trading rules with AI providing context only. Rules cannot be overridden.

## Technology Stack

- **Runtime**: Java 21 on AWS Lambda with SnapStart
- **Framework**: Spring Boot 3.2.1 + Spring Cloud Function
- **Database**: Amazon DynamoDB (On-Demand billing)
- **AI**: Spring AI 1.0.0-M4 with Claude API (claude-sonnet-4-5-20250929)
- **External APIs**: Binance Futures, Telegram Bot
- **Infrastructure**: AWS SAM / CloudFormation

## Essential Commands

### Build & Test
```bash
# Build project (skip tests)
./gradlew bootJar -x test

# Build with tests
./gradlew build

# Run tests only
./gradlew test

# Clean build
./gradlew clean build
```

### AWS Lambda Deployment
```bash
# Deploy to AWS (requires AWS CLI configured and S3 bucket)
./scripts/deploy.sh <your-s3-bucket-name>

# Example
./scripts/deploy.sh ai-trading-deployment-bucket
```

### Monitoring
```bash
# View Lambda logs in real-time
aws logs tail /aws/lambda/TradingEngineFunction --follow

# Check CloudFormation stack status
aws cloudformation describe-stacks --stack-name ai-trading-engine

# Get API Gateway endpoint
aws cloudformation describe-stacks \
  --stack-name ai-trading-engine \
  --query 'Stacks[0].Outputs'
```

### Configuration
```bash
# Update Lambda environment variables
aws lambda update-function-configuration \
  --function-name TradingEngineFunction \
  --environment Variables="{CLAUDE_API_KEY=your_key}"
```

## Architecture Overview

### Serverless Function-Based Architecture

The application uses **Spring Cloud Function** to expose 3 Lambda functions:

1. **processWebhook**: Handles TradingView webhook signals
   - Route: `POST /api/webhook/{proxy+}`
   - Input: `TradingViewWebhook`
   - Output: `{success, status, message, symbol}`

2. **getJournalEntries**: Queries trade journal
   - Route: `GET /api/journal/{proxy+}`
   - Input: Query parameters (symbol, quality)
   - Output: List of journal entries

3. **getStatistics**: Aggregated statistics
   - Route: `GET /api/journal/statistics`
   - Output: Win rate, P&L, quality breakdown

**Entry Point**: `StreamLambdaHandler::handleRequest` → Spring Cloud Function's `FunctionInvoker` routes to appropriate bean.

### Signal Processing Pipeline (9 Steps)

When a webhook arrives, `SignalProcessingService` orchestrates:

1. **Market Context Building**: Fetch real-time data from Binance (price, OI, funding, volatility)
2. **Intraday Context Engine**: Deterministic analysis of trend, OI behavior, volume
3. **AI Setup Assessment**: Claude analyzes market context (quality A/B/C, risks, invalidation)
4. **Rule Validation**: Enforce 6 non-negotiable trading rules
5. **Execution Planning**: Claude generates entry zones, stops, targets (if rules pass)
6. **Execution Advisory Checklist**: Pre-execution validation
7. **Trade Signal Creation**: Determine VALID/INVALID status
8. **Journal Entry Creation**: Persist to DynamoDB
9. **Telegram Notification**: Send alert (VALID signals only)

### Key Services

- **SignalProcessingService** (src/main/java/com/trading/engine/service/SignalProcessingService.java:*): Orchestrates 9-step pipeline
- **RuleEngineService** (src/main/java/com/trading/engine/service/RuleEngineService.java:*): Enforces 6 trading rules (cannot be overridden)
- **AiAnalysisService** (src/main/java/com/trading/engine/service/AiAnalysisService.java:*): Claude API integration with strategy-specific prompts
- **ContextBuilderService** (src/main/java/com/trading/engine/service/ContextBuilderService.java:*): Aggregates market data from Binance
- **IntradayContextEngine** (src/main/java/com/trading/engine/service/IntradayContextEngine.java:*): Deterministic intraday analysis
- **JournalService** (src/main/java/com/trading/engine/service/JournalService.java:*): DynamoDB persistence and queries
- **ExecutionAdvisoryService** (src/main/java/com/trading/engine/service/ExecutionAdvisoryService.java:*): Position sizing and execution guidance
- **NotificationService** (src/main/java/com/trading/engine/service/NotificationService.java:*): Telegram notifications

### Trading Rules (Non-Negotiable)

Defined in `application.yml` under `trading:` section:

1. **Setup Quality**: Only A/B quality setups (C blocked by default)
2. **HTF Alignment**: Required for trade validation
3. **Session Filtering**: London/NY sessions enabled, Asia disabled
4. **Daily Loss Limit**: Max 2R loss per day
5. **Open Interest Changes**: Must meet minimum threshold
6. **Volatility Control**: High volatility setups blocked

## Configuration

### Environment Variables (Required)

Set via AWS Lambda Console or CLI:

```bash
WEBHOOK_API_KEY=your_webhook_key
CLAUDE_API_KEY=your_claude_key
BINANCE_API_KEY=your_binance_key
BINANCE_API_SECRET=your_binance_secret
TELEGRAM_BOT_TOKEN=your_telegram_token
TELEGRAM_CHAT_ID=your_chat_id
```

### Application Configuration

Main config: `src/main/resources/application.yml`

- **Spring Cloud Function**: Defines 3 function beans
- **Trading Rules**: Non-negotiable risk/reward parameters
- **Claude AI**: Model (claude-sonnet-4-5-20250929), temperature (0.3), max tokens (1024)
- **Binance**: API endpoints, cache TTL (10s), timeout (10s)
- **Telegram**: Bot token, chat ID

### AWS Infrastructure

Defined in `template.yaml` (SAM/CloudFormation):

- **Lambda Function**: 2048MB memory, Java 21, SnapStart enabled
- **DynamoDB Table**: `journal_entries` with GSIs (by-symbol, by-quality)
- **API Gateway**: HTTP API with webhook/journal/stats routes
- **IAM Roles**: Lambda execution role with DynamoDB permissions

## Development Guidelines

### Code Structure

```
src/main/java/com/trading/engine/
├── config/              # Spring configurations, Lambda function beans
│   └── LambdaConfig.java          # 3 Spring Cloud Function beans
├── domain/              # Domain models, DTOs
│   ├── TradeSignal.java           # Complete signal with all analysis
│   ├── JournalEntry.java          # DynamoDB entity
│   ├── MarketContext.java         # Binance market data
│   └── AiAssessment.java          # Claude analysis results
├── service/             # Business logic (~3000+ lines)
│   ├── SignalProcessingService.java
│   ├── RuleEngineService.java
│   └── AiAnalysisService.java
├── repository/          # Data access
│   └── JournalEntryRepository.java  # DynamoDB Enhanced Client
└── StreamLambdaHandler.java       # AWS Lambda entry point
```

### Making Changes to Trading Rules

**IMPORTANT**: Trading rules in `application.yml` under `trading:` section are enforced by `RuleEngineService`. These rules have NO override mechanism by design. Changes to rule logic require:

1. Modify `TradingConfig.java` if adding new rule parameters
2. Update `RuleEngineService.validateSetup()` for rule logic changes
3. Update `application.yml` default values
4. Rebuild and redeploy

### Adding New Strategies

The system supports 3 strategy types (defined in `TradingViewWebhook.strategy`):
- LIQUIDITY_SWEEP
- CANDLE_2_CLOSURE
- BREAKOUT_RETEST

To add a new strategy:

1. Update `AiAnalysisService` with strategy-specific prompts:
   - `buildStrategySpecificContext()` method
   - `buildExecutionPrompt()` method
2. Add strategy enum value if needed
3. Update TradingView Pine Script to send new strategy value
4. Deploy updated function

### AI Integration

Claude API calls are in `AiAnalysisService`:
- **Setup Assessment**: Analyzes market context, returns quality (A/B/C), risks, invalidation
- **Execution Planning**: Generates entry zones, stop logic, targets, R:R ratios
- **Fallback**: `StaticAnalysisService` provides deterministic analysis if Claude unavailable

Configuration:
- Model: `claude-sonnet-4-5-20250929`
- Temperature: `0.3` (deterministic)
- Max tokens: `1024`
- Timeout: `30s`

### DynamoDB Schema

**Table**: `journal_entries`
- **Partition Key**: `signalId` (String)
- **GSI**: `by-symbol` (PK=symbol, SK=createdAt)
- **GSI**: `by-quality` (PK=setupQuality, SK=createdAt)

**Key Attributes** (54 total, nested objects stored as JSON strings):
- Core: symbol, direction, strategy, eventType, setupQuality, status
- Analysis: marketContext, aiAssessment, ruleResult (serialized JSON)
- Execution: entryPrice, stopLoss, takeProfit, riskRewardRatio
- Outcomes: tradeTaken, outcome, pnl, exitPrice, closedAt

### Testing

**Current State**: Minimal test coverage (only context load test exists)

**To Run Tests**:
```bash
./gradlew test
```

**Test Structure** (recommended):
```
src/test/java/com/trading/engine/
├── service/
│   ├── RuleEngineServiceTest.java
│   ├── SignalProcessingServiceTest.java
│   └── AiAnalysisServiceTest.java
└── integration/
    └── WebhookEndpointIntegrationTest.java
```

### Performance Considerations

- **Lambda Cold Start**: ~500ms with SnapStart (Java 21)
- **Spring Lazy Initialization**: Enabled for fast cold starts
- **Binance API Rate Limit**: 1200 weight/minute (monitored via `BinanceRateLimitFilter`)
- **Cache TTL**: 10 seconds for Binance market data (in-memory)
- **DynamoDB**: On-Demand billing, no provisioned capacity

## Common Workflows

### Deploying Updates
```bash
# Build and deploy
./gradlew bootJar -x test
./scripts/deploy.sh <your-s3-bucket-name>
```

### Testing Webhook via API Gateway
```bash
# Get API endpoint first
ENDPOINT=$(aws cloudformation describe-stacks \
  --stack-name ai-trading-engine \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiEndpoint`].OutputValue' \
  --output text)

# Send test webhook
curl -X POST ${ENDPOINT}/api/webhook \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your_webhook_key" \
  -d '{
    "symbol": "BTCUSDT",
    "direction": "LONG",
    "strategy": "LIQUIDITY_SWEEP",
    "eventType": "ENTRY"
  }'
```

### Querying Journal Entries
```bash
# All entries
curl ${ENDPOINT}/api/journal

# By symbol
curl ${ENDPOINT}/api/journal?symbol=BTCUSDT

# Statistics
curl ${ENDPOINT}/api/journal/statistics
```

### Updating Configuration Without Redeployment
Update environment variables via AWS CLI:
```bash
aws lambda update-function-configuration \
  --function-name TradingEngineFunction \
  --environment Variables={CLAUDE_API_KEY=new_key}
```

### Debugging Lambda Issues
```bash
# View CloudWatch logs
aws logs tail /aws/lambda/TradingEngineFunction --follow

# Check stack events
aws cloudformation describe-stack-events \
  --stack-name ai-trading-engine \
  --max-items 20

# Invoke function manually
aws lambda invoke \
  --function-name TradingEngineFunction \
  --payload '{"body":"{}"}' \
  response.json
```

## Important Notes

- **Rule Validation**: `RuleEngineService` has NO override mechanism. Failed rules = INVALID signal.
- **AI Role**: Claude provides context only, NOT trading decisions. Rules make final decision.
- **Async Processing**: Lambda is synchronous, but code supports `@Async` for future use.
- **Deduplication**: `IngressService` prevents duplicate webhook processing.
- **Notifications**: Telegram messages sent ONLY for VALID signals.
- **Branch**: Currently on `feature/aws-serverless-migration`, main branch is `develop`.

## Key Files Reference

- **Lambda Entry**: `src/main/java/com/trading/engine/StreamLambdaHandler.java`
- **Function Definitions**: `src/main/java/com/trading/engine/config/LambdaConfig.java`
- **Pipeline Orchestration**: `src/main/java/com/trading/engine/service/SignalProcessingService.java`
- **Trading Rules**: `src/main/java/com/trading/engine/service/RuleEngineService.java`
- **AI Integration**: `src/main/java/com/trading/engine/service/AiAnalysisService.java`
- **Infrastructure**: `template.yaml`
- **Main Config**: `src/main/resources/application.yml`
- **Build Config**: `build.gradle`

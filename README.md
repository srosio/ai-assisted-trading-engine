# AI-Assisted Crypto Trading Engine (AWS Serverless)

A professional, rule-based crypto trading system that uses AI **only** for market context analysis. Built with discipline, risk control, and auditability at its core.

**Fully serverless on AWS Lambda with DynamoDB.**

## Quick Start

```bash
# 1. Clone
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Build Project
./gradlew build -x test

# 3. Deploy to AWS (Requires AWS CLI configured)
./scripts/deploy.sh <your-s3-bucket-name>
```

## Features

- ✅ **Serverless Architecture**: Runs on AWS Lambda with SnapStart (Java 21) for zero-management scaling
- ✅ **Rule-Based Trading**: Strict, non-negotiable trading rules for risk management
- 🤖 **AI Context Analysis**: Claude AI provides market context (not trading decisions)
- 📊 **TradingView Integration**: Webhook-based signal processing via API Gateway
- 🔒 **Risk Management**: Position sizing, stop-loss, R-multiple tracking
- 📝 **NoSQL Journal**: High-performance trade journal using Amazon DynamoDB
- 🔔 **Telegram Notifications**: Real-time alerts for valid trade setups

## Architecture

**Technology Stack:**
- **Runtime**: Java 21 (AWS Lambda SnapStart)
- **Framework**: Spring Boot 3 + Spring Cloud Function
- **Database**: Amazon DynamoDB (On-Demand)
- **AI**: Spring AI with Claude (Anthropic)
- **Infrastructure**: AWS SAM / CloudFormation

**Serverless Benefits:**
- **No Server Management**: Lambda handles all infrastructure
- **Auto-Scaling**: Scales automatically with webhook volume
- **Pay-Per-Use**: Only pay for actual invocations
- **Fast Cold Starts**: ~500ms with SnapStart enabled
- **High Availability**: Built-in redundancy across AWS availability zones

## Project Structure

```
├── src/main/java/com/trading/engine/
│   ├── config/              # Lambda function beans & Spring configuration
│   ├── domain/              # DynamoDB entities & domain models
│   ├── service/             # Business logic (Rule Engine, Signal Processing)
│   ├── repository/          # DynamoDB Enhanced Client repositories
│   └── StreamLambdaHandler.java  # AWS Lambda entry point
├── template.yaml            # AWS Infrastructure Definition (SAM)
├── scripts/
│   └── deploy.sh           # Automated AWS deployment script
└── build.gradle            # Build configuration
```

## Trading Philosophy

This engine enforces **strict, non-negotiable rules**:

1. **Risk Management**: 0.5-1% risk per trade, 2R daily loss limit
2. **Session Filtering**: London/NY sessions (configurable)
3. **Setup Quality**: Only A/B quality setups (C blocked)
4. **Higher Timeframe Alignment**: Required for trade validation
5. **Volatility Control**: High volatility setups blocked
6. **AI for Context Only**: AI analyzes but doesn't decide

**The rules cannot be overridden.** AI provides context; rules make decisions.

## Deployment

### Prerequisites

1. AWS CLI installed and configured
2. An S3 bucket to store deployment artifacts
3. AWS credentials with permissions for:
   - CloudFormation
   - Lambda
   - DynamoDB
   - API Gateway
   - IAM roles

### Deploy to AWS

```bash
./scripts/deploy.sh my-deployment-bucket
```

This script:
1. Builds the JAR with `./gradlew bootJar -x test`
2. Packages CloudFormation template
3. Uploads artifacts to S3
4. Creates/updates CloudFormation stack with:
   - Lambda function (TradingEngineFunction)
   - DynamoDB table (journal_entries)
   - API Gateway HTTP API
   - IAM execution roles

### Configure Environment Variables

After deployment, set required environment variables:

```bash
aws lambda update-function-configuration \
  --function-name TradingEngineFunction \
  --environment Variables="{
    WEBHOOK_API_KEY=your_webhook_key,
    CLAUDE_API_KEY=your_claude_api_key,
    BINANCE_API_KEY=your_binance_key,
    BINANCE_API_SECRET=your_binance_secret,
    TELEGRAM_BOT_TOKEN=your_telegram_token,
    TELEGRAM_CHAT_ID=your_telegram_chat_id
  }"
```

### Get API Endpoint

```bash
aws cloudformation describe-stacks \
  --stack-name ai-trading-engine \
  --query 'Stacks[0].Outputs'
```

The output will show your API Gateway endpoint URL.

## Configuration

### Trading Rules (Non-Negotiable)

Configured in `src/main/resources/application.yml`:

```yaml
trading:
  min-risk-percent: 0.5
  max-risk-percent: 1.0
  min-risk-reward-ratio: 3.0
  max-daily-loss-r: 2.0

  london-session-enabled: true
  ny-session-enabled: true
  asia-session-enabled: false

  allow-a-quality: true
  allow-b-quality: true
  allow-c-quality: false

  require-htf-alignment: true
  block-high-volatility: true
```

### Claude AI Configuration

```yaml
claude:
  model: claude-sonnet-4-5-20250929
  max-tokens: 1024
  temperature: 0.3
  timeout-seconds: 30
```

### Binance API

```yaml
binance:
  base-url: https://fapi.binance.com
  cache-seconds: 10
  timeout-seconds: 10
```

## API Endpoints

The Lambda function exposes three Spring Cloud Functions via API Gateway:

### 1. Process Webhook (POST /api/webhook)

Receives TradingView webhook signals.

**Request:**
```json
{
  "symbol": "BTCUSDT",
  "direction": "LONG",
  "strategy": "LIQUIDITY_SWEEP",
  "eventType": "ENTRY",
  "timeframe": "15m"
}
```

**Response:**
```json
{
  "success": true,
  "status": "VALID",
  "message": "Trade signal validated",
  "symbol": "BTCUSDT"
}
```

### 2. Query Journal (GET /api/journal)

Retrieves trade journal entries.

**Query Parameters:**
- `symbol` (optional): Filter by trading pair
- `quality` (optional): Filter by setup quality (A/B/C)

### 3. Get Statistics (GET /api/journal/statistics)

Returns aggregated trading statistics.

**Response:**
```json
{
  "totalSignals": 150,
  "validSignals": 120,
  "invalidSignals": 30,
  "winRate": 65.5,
  "totalPnl": 15.3,
  "qualityBreakdown": {
    "A": 80,
    "B": 40,
    "C": 0
  }
}
```

## Monitoring & Debugging

### View Lambda Logs

```bash
# Real-time logs
aws logs tail /aws/lambda/TradingEngineFunction --follow

# Last 50 lines
aws logs tail /aws/lambda/TradingEngineFunction --since 1h
```

### Check Stack Status

```bash
aws cloudformation describe-stacks \
  --stack-name ai-trading-engine
```

### Invoke Function Manually

```bash
aws lambda invoke \
  --function-name TradingEngineFunction \
  --payload '{"body": "{\"symbol\":\"BTCUSDT\"}"}' \
  response.json
```

## Performance

- **Cold Start**: ~500ms with SnapStart
- **Warm Invocation**: ~50-100ms
- **Memory**: 2048MB (configurable in template.yaml)
- **Timeout**: 30 seconds
- **Concurrency**: Auto-scales up to account limit

## Cost Optimization

AWS Lambda pricing is pay-per-use:
- **Free Tier**: 1M requests/month, 400,000 GB-seconds compute
- **After Free Tier**: $0.20 per 1M requests + compute time

Typical costs for trading signals:
- 100 webhooks/day = ~3,000/month = **Free**
- 1,000 webhooks/day = ~30,000/month = **Free**
- DynamoDB on-demand pricing applies separately

## Development

### Build

```bash
# Build JAR
./gradlew bootJar

# Build with tests
./gradlew build

# Clean build
./gradlew clean build
```

### Run Tests

```bash
./gradlew test
```

### Update Deployment

```bash
# Make code changes, then redeploy
./gradlew bootJar -x test
./scripts/deploy.sh <your-s3-bucket-name>
```

## Signal Processing Pipeline

When a webhook arrives, the system executes a 9-step pipeline:

1. **Market Context Building**: Fetch real-time Binance data (price, OI, funding, volatility)
2. **Intraday Context Engine**: Deterministic analysis of market microstructure
3. **AI Setup Assessment**: Claude analyzes context and assigns quality (A/B/C)
4. **Rule Validation**: Enforce 6 non-negotiable trading rules
5. **Execution Planning**: Claude generates entry zones, stops, targets (if rules pass)
6. **Execution Advisory**: Create pre-execution checklist
7. **Trade Signal Creation**: Determine VALID/INVALID status
8. **Journal Persistence**: Save to DynamoDB with full analysis
9. **Telegram Notification**: Alert trader (VALID signals only)

## Support

For issues or questions:
- Review CloudWatch logs for Lambda execution errors
- Check CloudFormation events for deployment issues
- Verify environment variables are set correctly
- Ensure API keys have proper permissions

## License

Proprietary - All rights reserved

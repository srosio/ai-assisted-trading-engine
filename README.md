# AI-Assisted Crypto Trading Engine

A serverless, rule-based crypto trading system that processes TradingView webhook signals and uses Claude AI exclusively for market context analysis—NOT for making trade decisions. The system validates signals against strict, non-negotiable trading rules and maintains a comprehensive trade journal.

**Core Philosophy**: Strict, non-negotiable trading rules with AI providing context only. Rules cannot be overridden by AI or any other mechanism.

## How It Works

1. **TradingView sends webhook** when strategy conditions are met
2. **System fetches real-time market data** from Binance (price, open interest, funding, volatility)
3. **Intraday Context Engine** performs deterministic analysis of trends and market structure
4. **Claude AI analyzes setup quality** and assigns A/B/C grade with confidence score
5. **RuleEngineService validates** the signal against 6 non-negotiable rules
6. **If rules pass**: Claude generates execution plan (entry zones, stops, targets)
7. **Signal is classified** as TRADE (high confidence), WATCH (medium), or BLOCKED (failed rules)
8. **Complete analysis saved** to DynamoDB trade journal
9. **Telegram notification sent** for TRADE signals only

The AI never makes trading decisions—it only provides context. The rule engine has final authority.

## Quick Start

```bash
# Build
./gradlew shadowJar -x test

# Deploy
./scripts/deploy.sh <your-s3-bucket>

# Configure
aws lambda update-function-configuration \
  --function-name ai-trading-engine \
  --environment Variables="{CLAUDE_API_KEY=..., BINANCE_API_KEY=..., TELEGRAM_BOT_TOKEN=...}"
```

## Features

- ✅ **Rule-Based Trading**: Strict, non-negotiable trading rules with no AI override capability
- 🤖 **AI Context Analysis**: Claude AI provides market context only—rules make the final decisions
- 📊 **TradingView Integration**: Webhook-based signal processing for multiple strategies
- 🔒 **Risk Management**: Position sizing, stop-loss, R-multiple tracking, daily loss limits
- 📝 **Trade Journaling**: Comprehensive DynamoDB-based trade journal with GSI queries
- 🔔 **Telegram Notifications**: Real-time alerts for VALID trade setups only
- ⚡ **Rate Limit Protection**: Automatic Binance API rate limiting and back-off
- 💰 **Cost Optimization**: AI caching, smart gating, and Haiku pre-filtering (30-40% savings)

## Supported Trading Strategies

The system processes webhook signals from TradingView for the following strategy types:

1. **LIQUIDITY_SWEEP**
   - Identifies equal highs/lows swept with strong displacement
   - Higher risk-reward setups for volatile market conditions
   - AI analyzes liquidity level strength and sweep validity

2. **CANDLE_2_CLOSURE**
   - Two consecutive candles closing in the same direction
   - Combined with RSI extremes for reversal confirmation
   - Trend reversal and oversold/overbought setups

3. **BREAKOUT_RETEST**
   - Breakout from key levels with subsequent retest
   - Confirmation-based entries for trend continuation
   - AI assesses breakout strength and retest quality

**Note**: Strategy-specific prompts in `AiAnalysisService` provide tailored context analysis for each strategy type.

## Architecture

**Technology Stack**:
- Java 21 on AWS Lambda with SnapStart (~500ms cold start)
- Spring Boot 3.2.1 + Spring Cloud Function (3 Lambda functions)
- Spring AI 1.0.0-M4 with Claude API (claude-sonnet-4-5-20250929)
- Amazon DynamoDB (On-Demand billing) with GSI queries
- Binance Futures API for real-time market data
- Telegram Bot for notifications

**9-Step Signal Processing Pipeline** (`SignalProcessingService`):
1. **Market Context Building** - Fetch real-time data from Binance (price, OI, funding, volatility)
2. **Intraday Context Engine** - Deterministic analysis of trend, OI behavior, volume
3. **AI Setup Assessment** - Claude analyzes context → quality (A/B/C), risks, invalidation
4. **Rule Validation** - Enforce 6 non-negotiable trading rules via `RuleEngineService`
5. **Execution Planning** - AI generates entry zones, stops, targets (if rules pass)
6. **Execution Advisory** - Pre-execution validation checklist
7. **Signal Classification** - Determine TRADE/WATCH/BLOCKED status
8. **Journal Entry** - Persist complete analysis to DynamoDB
9. **Telegram Notification** - Send alert (TRADE signals only)

**Lambda Functions** (Spring Cloud Function beans):
- `processWebhook` - POST /api/webhook/{proxy+}
- `getJournalEntries` - GET /api/journal/{proxy+}
- `getStatistics` - GET /api/journal/statistics

## Trading Rules (Non-Negotiable)

The system enforces 6 strict trading rules via `RuleEngineService` with **NO override mechanism**:

1. **Setup Quality**: Only A/B quality setups (C quality blocked by default)
2. **HTF Alignment**: Higher timeframe bias alignment required for validation
3. **Session Filtering**: London/NY sessions enabled, Asia disabled (configurable)
4. **Daily Loss Limit**: Maximum 2R loss per day enforced
5. **Open Interest Changes**: Must meet minimum threshold for validation
6. **Volatility Control**: High volatility setups automatically blocked

**Critical**: These rules are defined in `application.yml` and enforced by `RuleEngineService`. Failed rules = INVALID signal. AI provides context only—rules make the final decision.

## Configuration

### Environment Variables

```bash
CLAUDE_API_KEY=your_key
BINANCE_API_KEY=your_key
BINANCE_API_SECRET=your_secret
TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_CHAT_ID=your_chat_id
WEBHOOK_API_KEY=your_webhook_key
```

### Trading Rules (`application.yml`)

```yaml
trading:
  min-risk-reward-ratio: 3.0
  allow-a-quality: true
  allow-b-quality: true
  allow-c-quality: false
```

## API Endpoints

### POST /api/webhook/tradingview

Receive TradingView signals and process through the 9-step pipeline.

**Request**:
```json
{
  "symbol": "BTCUSDT",
  "direction": "LONG",
  "strategy": "LIQUIDITY_SWEEP",
  "eventType": "ENTRY",
  "timeframe": "15",
  "session": "London",
  "price": {
    "close": 92100.50,
    "stopLoss": 91800.00
  },
  "context": {
    "htfBias": "bullish",
    "rsiValue": 28.5,
    "volumeSpike": true
  }
}
```

**Response**:
```json
{
  "success": true,
  "status": "VALID",
  "message": "Signal processed and journal entry created",
  "symbol": "BTCUSDT"
}
```

### GET /api/journal

Query trade history.

### GET /api/journal/statistics

Get performance stats (win rate, PnL, quality breakdown).

## TradingView Setup

1. Copy Pine scripts from `docs/tradingview/`
2. Add to TradingView chart
3. Create alert with webhook URL:
   ```
   https://<api-id>.execute-api.us-east-1.amazonaws.com/api/webhook/tradingview
   ```
4. Add header: `X-API-Key: your_webhook_key`

## Telegram Notifications

**Signal Classification**:
- 🟢 **TRADE**: High-confidence setup (A/B quality), all rules passed, ready to execute
- ⚠️ **WATCH**: Medium confidence, monitor for improvement (not sent by default)
- 🔴 **BLOCKED**: Failed rule validation, do not trade (not sent by default)

**Note**: Only TRADE signals trigger Telegram notifications to reduce noise.

### Market Context Explained
- **Trends**: 🟢 bullish / 🔴 bearish / ⚪ neutral across 15m/5m/1m timeframes
- **OI (Open Interest)**: Change % - positive = new positions opening, 🔥 >5% = strong activity
- **Funding**: Rate % - positive = longs pay shorts, ⚠️ >0.1% = extreme positioning
- **Volatility**: normal/expanding/contracting - affects stop placement

### TRADE Signal Example
```
🟢 TRADE SETUP - 🟢 A QUALITY

$BTC LONG
Liquidity Sweeps • reversal • London

💰 EXECUTION
Current: 92100.50
Entry: 92000.00 - 92200.00 (limit)
Stop Loss: 91800.00 (-1.2%)
Targets:
  T1: 92500.00 (3.0R)
  T2: 93000.00 (5.0R)

📊 MARKET INSIGHT
Confidence: 85/100
Trends: 🟢/🟢/⚪ (15m/5m/1m)
Setup: Long Buildup 📈
OI: +3.2% 🔥
Funding: +0.0125% ⚠️
Volatility: normal

✅ ACTION: READY TO TRADE
```

### BLOCKED Signal Example
```
🔴 BLOCKED SIGNAL

$BTC LONG
Candle 2 Closure • reversal • Asia

❌ REASON
Setup quality too low for consideration

📊 DETAILS
Quality: C
Confidence: 45/100
OI: -0.8%
Funding: +0.0050%

⛔ ACTION: DO NOT TRADE
```

## Monitoring

```bash
# View logs
aws logs tail /aws/lambda/ai-trading-engine --follow

# Get API endpoint
aws cloudformation describe-stacks --stack-name ai-trading-engine

# Test webhook
curl -X POST <api-url> -H "X-API-Key: key" -d @test-payloads/example.json
```

## Development

```bash
# Build
./gradlew shadowJar

# Test
./gradlew test

# Deploy
./scripts/deploy.sh <bucket>
```

## Performance

- **Response Time**: 1-3 seconds (includes AI analysis and market data fetching)
- **Cold Start**: ~500ms with Lambda SnapStart (Java 21)
- **Memory**: 2048MB Lambda allocation
- **Timeout**: 30 seconds
- **Binance API Cache**: 10-second TTL for market data (in-memory)
- **Rate Limiting**: Automatic Binance API rate limit monitoring and backoff
- **Cost**: AWS Free Tier covers ~1M Lambda requests/month; Claude API usage optimized with caching

## Project Structure

```
src/main/java/com/trading/engine/
├── config/          # Spring & AWS configuration
├── domain/          # Data models (webhook, signal, context)
├── service/         # Business logic (AI, rules, processing)
├── repository/      # DynamoDB repositories
└── StreamLambdaHandler.java  # Lambda entry point

docs/tradingview/    # Pine scripts
template.yaml        # AWS infrastructure
```

## Cost Optimization

The system includes multiple strategies to minimize Claude API costs:

- **Smart Gating**: Skip AI analysis for signals below confidence threshold (default: 60) - saves 30-40%
- **Haiku Pre-filter**: Use `claude-haiku-4-20250514` for initial screening (90% cheaper than Sonnet)
- **AI Response Caching**: DynamoDB-based cache with TTL reuses similar market analyses - saves 20-30%
- **Lambda SnapStart**: Reduces cold start time and compute costs
- **DynamoDB On-Demand**: Pay only for actual read/write requests

**Configuration**: All cost optimization features are configurable in `application.yml` under `ai.cost-optimization`

## License

Proprietary

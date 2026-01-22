# AI-Assisted Crypto Trading Engine

Rule-based crypto trading system with AI market analysis. Serverless on AWS Lambda.

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

- 🤖 **AI Analysis**: Claude analyzes market context (RSI, ATR, candle patterns)
- 📊 **TradingView Integration**: Webhook-based signals from Pine scripts
- 🔒 **Rule-Based**: 6 non-negotiable rules (risk, session, quality, alignment)
- 📱 **Telegram Alerts**: Real-time notifications with execution details
- 📝 **Trade Journal**: DynamoDB storage with performance tracking
- ⚡ **Serverless**: AWS Lambda + API Gateway + DynamoDB

## Architecture

**Stack**: Java 21, Spring Boot 3, AWS Lambda (SnapStart), DynamoDB, Claude AI

**Pipeline**: Webhook → Validate → Market Data → AI Analysis → Rules → Journal → Telegram

## Trading Rules

1. **Sessions**: London/NY only (configurable)
2. **Quality**: A/B setups only (C rejected)
3. **Alignment**: HTF bias required
4. **Volatility**: High volatility blocked
5. **AI Role**: Context only, not decisions

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

Receive TradingView signals.

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Liquidity Sweeps",
  "eventType": "reversal",
  "direction": "bullish",
  "session": "London",
  "price": {"close": 92100.50, "stopLoss": 91800.00},
  "context": {
    "htfBias": "bullish",
    "rsiValue": 28.5,
    "atrValue": 150.25,
    "volumeSpike": true
  }
}
```

**Response**: `202 Accepted`

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

### Market Context Explained
- **Trends**: 🟢 bullish / 🔴 bearish / ⚪ neutral across 15m/5m/1m timeframes
- **OI (Open Interest)**: Change % - positive = new positions opening, 🔥 >5% = strong activity
- **Funding**: Rate % - positive = longs pay shorts, ⚠️ >0.1% = extreme positioning
- **Volatility**: normal/expanding/contracting - affects stop placement

### Valid Signal
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

### Invalid Signal
```
🔴 SETUP REJECTED

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

- **Response Time**: 1-3 seconds (includes AI analysis)
- **Cold Start**: ~500ms (SnapStart)
- **Memory**: 2GB
- **Timeout**: 30s
- **Cost**: Free tier covers ~30k webhooks/month

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

- **Smart Gating**: Skip AI for low-confidence signals (30-40% savings)
- **Haiku Pre-filter**: Use cheaper model first (90% cheaper)
- **Caching**: Reuse similar analyses (20-30% savings)
- **Lambda Free Tier**: 1M requests/month

## License

Proprietary

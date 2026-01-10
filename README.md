# AI-Assisted Crypto Trading Engine

A professional, rule-based crypto trading system that uses AI **only** for market context analysis. Built with discipline, risk control, and auditability at its core.

## Core Principles

**AI is used ONLY as a context analyst, NEVER as a trader.**

### What AI Cannot Do
- ❌ Decide buy/sell
- ❌ Modify risk parameters
- ❌ Override rules
- ❌ Make trading decisions

### What AI Can Do
- ✅ Assess setup quality (A/B/C)
- ✅ Identify risk factors
- ✅ Provide invalidation criteria
- ✅ Analyze market context alignment

### Non-Negotiable Rules
All trades must pass:
1. **Structural rules** (HTF alignment, session validation)
2. **Risk rules** (0.5-1% risk, min 3:1 R:R)
3. **Session rules** (London/NY only by default)

**If rules fail, the trade is blocked even if AI analysis is positive.**

## Architecture

```
TradingView (Pine Script)
        ↓ Webhook
Spring Boot API
        ↓
Market Data Service (Binance)
        ↓
Context Builder (Factual snapshot)
        ↓
Spring AI + Claude Analysis (Constrained)
        ↓
Rule Engine (Hard validation)
        ↓
Risk Engine (Position sizing)
        ↓
Telegram Notification + Journal
```

## Recent Updates

### API Modernization (Latest)
- ✅ **camelCase JSON** - All API requests and responses now use camelCase field names (e.g., `previousDayHigh`, `setupQuality`)
- ✅ **Spring AI Integration** - Replaced manual Claude API calls with Spring AI framework for cleaner, declarative AI integration
- ✅ **API Key Authentication** - Added Spring Security with API key authentication to protect webhook endpoints
- ✅ **Pine Script v6** - Updated TradingView integration to use Pine Script version 6
- ✅ **Java 21 Patterns** - Applied modern Java patterns throughout (var, final, List.of(), Map.of())

## Tech Stack

- **Java 21** with modern patterns (var, final, records)
- **Spring Boot 3.2.1**
- **Spring AI** (Anthropic integration for Claude)
- **Spring Security** (API key authentication)
- **Gradle 8.14.3** with wrapper (no installation needed)
- **PostgreSQL** (journal & stats)
- **Redis** (market data cache)
- **Binance Futures API** (market data)
- **Telegram Bot API** (notifications)
- **Mock Mode** for testing without API keys

## 🚀 Quick Start

### Option 1: Mock Mode (No API Keys Required)

Test the system immediately with simulated data:

```bash
# Linux/Mac
./gradlew bootRun --args='--spring.profiles.active=mock'

# Windows
gradlew.bat bootRun --args="--spring.profiles.active=mock"
```

**Mock mode provides:**
- ✅ Simulated market data (realistic prices, OI, funding)
- ✅ AI-like analysis responses (quality ratings, risk factors)
- ✅ Console logging instead of Telegram
- ✅ H2 in-memory database (no PostgreSQL needed)
- ✅ Perfect for testing and development

Test the webhook:
```bash
curl -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "symbol": "BTCUSDT",
    "timeframe": "5m",
    "event": "liquidity_sweep_long",
    "session": "NY",
    "price": 43120.50,
    "previousDayHigh": 43210.00,
    "previousDayLow": 42880.00,
    "volumeSpike": true,
    "htfBias": "bullish"
  }'
```

**Note:** The API uses camelCase for all JSON fields. Set `WEBHOOK_API_KEY` environment variable for authentication.

**You'll see detailed logs in the console showing the entire signal processing pipeline!**

### Option 2: Production Mode (Real API Keys)

For actual trading with real market data - see [Setup Instructions](#setup-instructions) below.

## Project Structure

```
src/main/java/com/trading/engine/
├── controller/
│   └── WebhookController.java          # Webhook endpoint
├── service/
│   ├── MarketDataService.java          # Binance market data
│   ├── ContextBuilderService.java      # Build factual context
│   ├── AiAnalysisService.java          # Claude AI (constrained)
│   ├── RuleEngineService.java          # Hard validation rules
│   ├── RiskEngineService.java          # Position sizing & limits
│   ├── NotificationService.java        # Telegram alerts
│   ├── JournalService.java             # Trade logging
│   └── SignalProcessingService.java    # Main orchestration
├── domain/
│   ├── TradingViewWebhook.java         # Webhook payload
│   ├── MarketContext.java              # Market snapshot
│   ├── AiAssessment.java               # AI analysis result
│   ├── RuleResult.java                 # Validation result
│   ├── RiskCalculation.java            # Risk parameters
│   ├── TradeSignal.java                # Complete signal
│   └── JournalEntry.java               # Persistent record
├── config/
│   ├── SpringAiConfig.java             # Spring AI & Anthropic setup
│   ├── SecurityConfig.java             # API key authentication
│   ├── ClaudeConfig.java               # Claude API config
│   ├── BinanceConfig.java              # Binance config
│   ├── TelegramConfig.java             # Telegram config
│   └── TradingConfig.java              # Trading rules (NON-NEGOTIABLE)
└── repository/
    └── JournalEntryRepository.java     # Database access
```

## Setup Instructions

Choose your deployment method:
- **☁️ Cloud (Recommended):** Deploy to Fly.io in 5 minutes → [Fly.io Guide](docs/FLYIO_DEPLOYMENT.md)
- **💻 Local Development:** Run on your machine → Instructions below

### Local Development Setup

#### 1. Prerequisites

- Java 21 (Gradle wrapper included - no Gradle installation needed)
- PostgreSQL 14+ (optional - use mock mode for testing)
- Redis 6+ (optional - use mock mode for testing)
- Claude API key (optional - use mock mode for testing)
- Binance Futures account (optional - use mock mode for testing)
- Telegram bot token (optional - use mock mode for testing)

#### 2. Database Setup

```bash
# Create PostgreSQL database
createdb trading_engine

# Redis should be running on default port 6379
redis-server
```

#### 3. Configuration

Copy the example configuration:

```bash
cp src/main/resources/application-example.yml src/main/resources/application-local.yml
```

Edit `application-local.yml` with your credentials:

```yaml
# Webhook API Security
webhook:
  api:
    key: your-secure-webhook-api-key

claude:
  api-key: your_claude_api_key_here

binance:
  api-key: your_binance_api_key
  api-secret: your_binance_api_secret

telegram:
  bot-token: your_telegram_bot_token
  chat-id: your_telegram_chat_id

trading:
  account-balance: 10000  # Your account size
```

Or set environment variables:
```bash
export WEBHOOK_API_KEY=your-secure-webhook-api-key
export CLAUDE_API_KEY=your_claude_api_key_here
export BINANCE_API_KEY=your_binance_api_key
export BINANCE_API_SECRET=your_binance_api_secret
export TELEGRAM_BOT_TOKEN=your_telegram_bot_token
export TELEGRAM_CHAT_ID=your_telegram_chat_id
export ACCOUNT_BALANCE=10000
```

#### 4. Build & Run

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Or run the JAR
java -jar build/libs/ai-assisted-trading-engine-1.0.0.jar
```

The application will start on `http://localhost:8080`

#### 5. Verify Setup

Check the health endpoint:
```bash
curl http://localhost:8080/api/webhook/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "AI-Assisted Trading Engine"
}
```

## TradingView Integration

### Pine Script Setup

Your Pine Script (v6) should detect objective market events and send webhooks when structural conditions are met.

**Example events:**
- Liquidity sweep (equal highs/lows taken)
- Session high/low break
- Displacement candle
- Volume expansion

See [docs/PINE_SCRIPT_EXAMPLE.pine](docs/PINE_SCRIPT_EXAMPLE.pine) for a complete Pine Script v6 implementation.

### Webhook Payload Format

**All JSON fields use camelCase:**

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "liquidity_sweep_long",
  "session": "NY",
  "price": 43120,
  "previousDayHigh": 43210,
  "previousDayLow": 42880,
  "volumeSpike": true,
  "displacementDetected": true,
  "htfBias": "bullish"
}
```

### Webhook URL

Configure TradingView to POST to:
```
http://your-server:8080/api/webhook/tradingview
```

**Authentication:** Include your API key in one of two ways:
- Header: `X-API-Key: your-secret-api-key`
- Query parameter: `?apiKey=your-secret-api-key`

## Configuration Reference

### Trading Rules (application.yml)

```yaml
trading:
  # Risk Management (NON-NEGOTIABLE)
  min-risk-percent: 0.5
  max-risk-percent: 1.0
  default-risk-percent: 1.0
  min-risk-reward-ratio: 3.0
  max-trades-per-day: 2
  max-daily-loss-r: 2.0

  # Account
  account-balance: 10000

  # Session Rules
  london-session-enabled: true
  ny-session-enabled: true
  asia-session-enabled: false

  # Setup Quality
  allow-a-quality: true
  allow-b-quality: true
  allow-c-quality: false  # Block low quality

  # Alignment
  require-htf-alignment: true

  # Volatility
  block-high-volatility: true
  max-volatility-threshold: 2.5

  # Open Interest
  min-oi-change-percent: 2.0
```

## Signal Processing Pipeline

When a webhook is received:

1. **Authentication** - Validate API key from X-API-Key header or apiKey parameter
2. **Context Building** - Fetch real-time data from Binance (price, OI, funding rate, volatility)
3. **AI Analysis** - Send context to Claude via Spring AI with strict constraints (quality rating, risks, invalidation)
4. **Rule Validation** - Check all hard rules (quality, HTF alignment, session, trade limits)
5. **Risk Calculation** - Calculate position size, stop loss, take profit (min 1:3 R:R)
6. **Signal Creation** - Combine all results into trade signal
7. **Journal Entry** - Automatically log everything to database
8. **Telegram Notification** - Send formatted alert to trader

## Telegram Notification Format

```
✅ TRADE SIGNAL

Symbol: BTCUSDT
Direction: LONG
Event: liquidity_sweep_long
Session: NY

AI Quality: A
Alignment: 85/100
Key Risk: HTF resistance nearby

Rule Check: ✅ PASS

Risk Management:
Entry: 43120
Stop: 42900
Target: 43780
Size: 0.045
R:R: 1:3.0

Action: Monitor per trading plan
Invalidation: Break below swept low
```

## Database Schema

The system automatically creates the `journal_entries` table with:

- Signal metadata (ID, symbol, direction, event)
- Complete webhook payload
- Market context snapshot
- AI assessment
- Rule validation result
- Risk parameters
- Manual outcome fields (filled by trader later)

## Querying Journal Data

```sql
-- Today's signals
SELECT * FROM v_today_trades;

-- Performance by setup quality
SELECT * FROM v_performance_stats;

-- Win rate for A-quality setups
SELECT
    COUNT(*) as total,
    COUNT(CASE WHEN outcome = 'WIN' THEN 1 END) as wins,
    ROUND(COUNT(CASE WHEN outcome = 'WIN' THEN 1 END)::NUMERIC / COUNT(*)::NUMERIC * 100, 2) as win_rate
FROM journal_entries
WHERE setup_quality = 'A' AND trade_taken = true;
```

## API Endpoints

### POST /api/webhook/tradingview
Receive TradingView webhook

**Authentication:** Required via `X-API-Key` header or `apiKey` query parameter
**Request:** JSON payload with camelCase field names
**Response:** JSON with signal processing results

Example response:
```json
{
  "success": true,
  "signalId": "abc123",
  "status": "APPROVED",
  "action": "MONITOR",
  "setupQuality": "A",
  "rulesPassed": true
}
```

### GET /api/webhook/health
Health check endpoint (no authentication required)

**Response:**
```json
{
  "status": "UP",
  "service": "AI-Assisted Trading Engine"
}
```

### POST /api/webhook/test
Test signal processing (for development)

**Authentication:** Required via `X-API-Key` header or `apiKey` query parameter
**Response:** Full signal object with all processing details

## Security Considerations

1. **API Keys** - Store all API keys in environment variables, never commit to git
2. **Webhook Authentication** - All webhook endpoints (except /health) require API key authentication via:
   - `X-API-Key` HTTP header (recommended), or
   - `apiKey` query parameter
3. **Spring Security** - Stateless API key authentication filter protects all endpoints
4. **Database** - Use strong passwords and restrict access
5. **Rate Limiting** - Consider adding rate limits on webhook endpoint if needed
6. **Logging** - Sensitive data is not logged
7. **HTTPS** - Always use HTTPS in production to protect API keys in transit

## Monitoring

Monitor the logs for:
- Webhook reception
- AI analysis results
- Rule validation failures
- Risk limit violations
- Database writes
- Telegram delivery

## Troubleshooting

### AI Analysis Fails
The system creates a fallback assessment with quality "C" and requires manual review.

### Rules Block Everything
Check your trading config - you may have sessions disabled or strict quality requirements.

### No Telegram Notifications
Verify bot token and chat ID are correct. Check logs for Telegram API errors.

### Database Connection Issues
Ensure PostgreSQL is running and credentials are correct in application.yml.

## Development

### Running Tests
```bash
mvn test
```

### Build Docker Image
```bash
docker build -t trading-engine:latest .
```

## Important Notes

1. **This system does NOT auto-trade** - It assists human decision-making
2. **All trades require human confirmation** - The system provides analysis, not orders
3. **AI constraints are hardcoded** - Cannot be bypassed without code changes
4. **Risk rules are non-negotiable** - Enforced at the code level
5. **Everything is journaled** - For accountability and learning

## License

Proprietary - For personal use only

## Support

For issues or questions, review the logs first. Most problems are configuration-related.

---

**Remember: AI assists, rules decide, humans trade.**

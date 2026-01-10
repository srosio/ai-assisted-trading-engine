# Configuration Guide

## Overview

The AI-Assisted Trading Engine is configured through `application.yml`. All trading rules are **NON-NEGOTIABLE** and enforced at the code level.

## Configuration Sections

### 1. Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/trading_engine
    username: postgres
    password: ${DB_PASSWORD:postgres}
```

**Environment Variables:**
- `DB_PASSWORD` - PostgreSQL password

---

### 2. Redis Cache Configuration

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
```

Market data is cached for 10 seconds to reduce API calls.

---

### 3. Claude AI Configuration

```yaml
claude:
  api-key: ${CLAUDE_API_KEY}
  model: claude-3-5-sonnet-20241022
  max-tokens: 1024
  temperature: 0.3
  timeout-seconds: 30
```

**Environment Variables:**
- `CLAUDE_API_KEY` - Your Anthropic API key

**Important Settings:**
- `temperature: 0.3` - Low for consistent, factual analysis
- `max-tokens: 1024` - Sufficient for structured JSON responses
- Model is hardcoded to Sonnet for reliability

---

### 4. Binance Configuration

```yaml
binance:
  api-key: ${BINANCE_API_KEY}
  api-secret: ${BINANCE_API_SECRET}
  base-url: https://fapi.binance.com
  cache-seconds: 10
```

**Environment Variables:**
- `BINANCE_API_KEY` - Binance Futures API key
- `BINANCE_API_SECRET` - Binance API secret

**Note:** Only market data endpoints are used. No order placement.

---

### 5. Telegram Configuration

```yaml
telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
  chat-id: ${TELEGRAM_CHAT_ID}
  enabled: true
  retry-attempts: 3
```

**Environment Variables:**
- `TELEGRAM_BOT_TOKEN` - Your bot token from @BotFather
- `TELEGRAM_CHAT_ID` - Your personal chat ID

**Getting Chat ID:**
```bash
# Send message to your bot, then:
curl https://api.telegram.org/bot<TOKEN>/getUpdates
```

---

### 6. Trading Rules (NON-NEGOTIABLE)

#### Risk Management

```yaml
trading:
  min-risk-percent: 0.5
  max-risk-percent: 1.0
  default-risk-percent: 1.0
  min-risk-reward-ratio: 3.0
  max-trades-per-day: 2
  max-daily-loss-r: 2.0
```

**Explanation:**
- **Risk per trade:** 0.5% - 1.0% of account
- **R:R ratio:** Minimum 1:3 (risk 1 to make 3)
- **Max trades:** 2 per day (prevents overtrading)
- **Daily loss limit:** -2R maximum (stops trading after -2R loss)

**These cannot be overridden by AI or manual intervention.**

---

#### Account Configuration

```yaml
trading:
  account-balance: ${ACCOUNT_BALANCE:10000}
```

**Environment Variable:**
- `ACCOUNT_BALANCE` - Your trading account size

Used for position sizing calculations.

---

#### Session Rules

```yaml
trading:
  london-session-enabled: true
  ny-session-enabled: true
  asia-session-enabled: false
```

**Session Times (EST):**
- London: 3:00 AM - 11:00 AM
- NY: 9:00 AM - 4:00 PM
- Asia: 8:00 PM - 4:00 AM

Disable sessions you don't want to trade.

---

#### Setup Quality Rules

```yaml
trading:
  allow-a-quality: true
  allow-b-quality: true
  allow-c-quality: false
```

- **A Quality:** Best setups (always allowed)
- **B Quality:** Good setups
- **C Quality:** Low quality (blocked by default)

AI assigns quality ratings. Rules enforce which you'll accept.

---

#### Alignment Requirements

```yaml
trading:
  require-htf-alignment: true
```

When true, Higher Time Frame (HTF) bias must align with trade direction.

Example:
- Long setup requires HTF bias = "bullish"
- Short setup requires HTF bias = "bearish"

---

#### Volatility Rules

```yaml
trading:
  block-high-volatility: true
  max-volatility-threshold: 2.5
```

Blocks trades during excessive volatility (measured by ATR ratio).

---

#### Open Interest Rules

```yaml
trading:
  min-oi-change-percent: 2.0
```

Requires minimum 2% change in Open Interest to validate setup.

Low OI change suggests weak conviction.

---

## Environment Variables

Create a `.env` file (never commit this):

```bash
# Database
DB_PASSWORD=your_secure_password

# Redis
REDIS_PASSWORD=your_redis_password

# APIs
CLAUDE_API_KEY=sk-ant-xxxxx
BINANCE_API_KEY=your_binance_key
BINANCE_API_SECRET=your_binance_secret

# Telegram
TELEGRAM_BOT_TOKEN=123456:ABC-DEF
TELEGRAM_CHAT_ID=your_chat_id

# Trading
ACCOUNT_BALANCE=10000
```

---

## Profile-Specific Configuration

### Local Development

Create `application-local.yml`:

```yaml
logging:
  level:
    com.trading.engine: DEBUG

telegram:
  enabled: false  # Disable notifications during testing
```

Run with: `mvn spring-boot:run -Dspring.profiles.active=local`

---

### Production

Create `application-prod.yml`:

```yaml
logging:
  level:
    root: WARN
    com.trading.engine: INFO

spring:
  jpa:
    show-sql: false
```

---

## Configuration Validation

The system validates configuration on startup:

1. **Required fields** - Claude API key, Binance credentials
2. **Risk rules** - Must be within sensible ranges
3. **Database connection** - Must be accessible
4. **Redis connection** - Must be available

If validation fails, the application will not start.

---

## Tuning Recommendations

### Conservative Settings (Recommended for Beginners)
```yaml
trading:
  default-risk-percent: 0.5
  max-trades-per-day: 1
  allow-b-quality: false
  require-htf-alignment: true
```

### Aggressive Settings (Experienced Traders Only)
```yaml
trading:
  default-risk-percent: 1.0
  max-trades-per-day: 2
  allow-b-quality: true
  require-htf-alignment: false
```

### Safe Testing
```yaml
trading:
  account-balance: 100  # Small test size
  default-risk-percent: 0.5
  max-trades-per-day: 5
  telegram:
    enabled: true
```

---

## Security Best Practices

1. **Never commit** API keys or passwords
2. **Use environment variables** for all secrets
3. **Rotate keys regularly** (especially Binance)
4. **Restrict Binance API** to read-only + futures (no withdrawals)
5. **Use strong passwords** for PostgreSQL
6. **Enable Redis authentication** in production
7. **Use HTTPS** for webhook endpoint
8. **Implement webhook signature verification**

---

## Troubleshooting

### Application Won't Start
- Check all required environment variables are set
- Verify database is running and accessible
- Check Redis is available
- Review logs for validation errors

### No Signals Received
- Verify webhook URL is correct
- Check TradingView alert is active
- Review firewall rules
- Check application logs

### All Signals Blocked
- Review `trading` configuration
- Check if sessions are enabled
- Verify quality settings
- Check daily trade limit

---

## Advanced Configuration

### Custom Cache TTL

```yaml
spring:
  cache:
    redis:
      time-to-live: 5000  # 5 seconds
```

### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### Request Timeouts

```yaml
claude:
  timeout-seconds: 60  # Increase for slow responses

binance:
  timeout-seconds: 15
```

---

**Remember: Configuration controls behavior, but core principles are non-negotiable.**

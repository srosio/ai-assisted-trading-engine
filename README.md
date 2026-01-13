# AI-Assisted Crypto Trading Engine

A professional, rule-based crypto trading system that uses AI **only** for market context analysis. Built with discipline, risk control, and auditability at its core.

## Quick Start

```bash
# 1. Clone and setup
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Configure environment
cp .env.example .env
nano .env  # Add your API keys

# 3. Start services
docker-compose up -d

# 4. Test the API
curl http://localhost:8080/api/webhook/health
```

**See [SETUP.md](SETUP.md) for complete installation instructions.**

## Features

- ✅ **Rule-Based Trading**: Strict, non-negotiable trading rules for risk management
- 🤖 **AI Context Analysis**: Claude AI provides market context (not trading decisions)
- 📊 **TradingView Integration**: Webhook-based signal processing from Pine Script strategies
- 🔒 **Risk Management**: Position sizing, stop-loss, R-multiple tracking
- 📝 **Trade Journaling**: Comprehensive PostgreSQL-based trade journal
- 🔔 **Telegram Notifications**: Real-time alerts for valid trade setups
- ⚡ **Rate Limit Protection**: Automatic Binance API rate limiting and back-off
- 🐳 **Docker Support**: Easy deployment with Docker Compose

## Architecture

**Technology Stack:**
- Java 21, Spring Boot 3.2
- PostgreSQL 14 (persistent storage)
- Redis 7 (caching)
- Spring AI with Claude (Anthropic)
- Binance Futures API
- Telegram Bot API

**AI Usage:**
- Market context analysis only (trend, sentiment, key levels)
- Trading decisions driven by rules, not AI
- Fully auditable AI interactions

## Documentation

- **[SETUP.md](SETUP.md)** - Complete setup and installation guide
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide for contributors
- **[docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md)** - REST API documentation
- **[scripts/README.md](scripts/README.md)** - Deployment and service management

## Project Structure

```
├── src/main/java/com/trading/engine/
│   ├── config/              # Configuration (Security, APIs, Rate Limiting)
│   ├── controller/          # REST endpoints
│   ├── service/             # Business logic
│   ├── model/               # Domain models
│   └── repository/          # Data access
├── docs/                    # Documentation
├── scripts/                 # Deployment scripts
└── docker-compose.yml       # Local development setup
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

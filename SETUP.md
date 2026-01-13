# AI-Assisted Trading Engine - Setup Guide

Complete guide for setting up and running the AI-Assisted Crypto Trading Engine.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Detailed Setup](#detailed-setup)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

1. **Java 21** (OpenJDK or Oracle JDK)
   ```bash
   java -version  # Should show version 21+
   ```
   Download: https://adoptium.net/

2. **PostgreSQL 14+** (for production)
   ```bash
   psql --version
   ```
   Download: https://www.postgresql.org/download/

3. **Redis 7+** (for caching)
   ```bash
   redis-cli --version
   ```
   Download: https://redis.io/download/

4. **Docker & Docker Compose** (recommended for easy setup)
   ```bash
   docker --version
   docker-compose --version
   ```
   Download: https://docs.docker.com/get-docker/

### Required API Keys

You'll need accounts and API keys for:

1. **Claude AI** (Anthropic)
   - Create account: https://console.anthropic.com/
   - Generate API key: https://console.anthropic.com/settings/keys
   - Documentation: https://docs.anthropic.com/

2. **Binance API**
   - Create account: https://www.binance.com/
   - Generate API key: https://www.binance.com/en/my/settings/api-management
   - **For testing**: Use Binance Testnet: https://testnet.binance.vision/
   - ⚠️ **Important**: Enable "Futures" permissions for your API key

3. **Telegram Bot**
   - Create bot: Message @BotFather on Telegram, use `/newbot`
   - Get your chat ID: Message @userinfobot on Telegram
   - Documentation: https://core.telegram.org/bots

---

## Quick Start

### Option 1: Docker Compose (Recommended for Development)

```bash
# 1. Clone the repository
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Create environment file
cp .env.example .env
nano .env  # Configure your API keys

# 3. Start all services
docker-compose up -d

# 4. Check logs
docker-compose logs -f app

# 5. Test health endpoint
curl http://localhost:8080/api/webhook/health
```

### Option 2: Local Development

```bash
# 1. Clone the repository
git clone <repository-url>
cd ai-assisted-trading-engine

# 2. Create environment file
cp .env.example .env
nano .env  # Configure your API keys

# 3. Start database services
docker-compose up -d postgres redis

# 4. Build the application
./gradlew build

# 5. Run the application
./scripts/run.sh

# 6. Test health endpoint
curl http://localhost:8080/api/webhook/health
```

### Option 3: Mock Mode (No API Keys Required)

Perfect for testing the application without external dependencies:

```bash
# 1. Clone and setup
git clone <repository-url>
cd ai-assisted-trading-engine
cp .env.example .env

# 2. Set mock mode in .env
echo "SPRING_PROFILES_ACTIVE=mock" >> .env

# 3. Start database services
docker-compose up -d postgres redis

# 4. Build and run
./gradlew build
./scripts/run.sh
```

---

## Detailed Setup

### 1. Database Setup

#### Using Docker (Recommended)

```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Verify PostgreSQL is running
docker exec -it trading_engine_db psql -U postgres -c '\l'

# Verify Redis is running
docker exec -it trading_engine_redis redis-cli ping
```

#### Manual PostgreSQL Setup

```bash
# Create database
sudo -u postgres psql
CREATE DATABASE trading_engine;
CREATE USER trading_user WITH PASSWORD 'your-password';
GRANT ALL PRIVILEGES ON DATABASE trading_engine TO trading_user;
\q

# Update .env with your credentials
DB_URL=jdbc:postgresql://localhost:5432/trading_engine
DB_USERNAME=trading_user
DB_PASSWORD=your-password
```

#### Manual Redis Setup

```bash
# Start Redis
redis-server

# Test connection
redis-cli ping  # Should return PONG
```

### 2. Environment Configuration

Create your `.env` file from the example:

```bash
cp .env.example .env
```

Edit `.env` and configure **all required variables**:

```bash
# Generate a secure webhook key
WEBHOOK_API_KEY=$(openssl rand -hex 32)

# Add your Claude AI API key
CLAUDE_API_KEY=sk-ant-api03-xxxxxxxxxxxx

# Add your Binance API credentials
BINANCE_API_KEY=your-binance-api-key
BINANCE_API_SECRET=your-binance-api-secret

# Add your Telegram bot credentials
TELEGRAM_BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_CHAT_ID=987654321

# Database (if not using defaults)
DB_PASSWORD=root

# Trading configuration
ACCOUNT_BALANCE=10000
SPRING_PROFILES_ACTIVE=dev
```

### 3. Build the Application

```bash
# Clean build
./gradlew clean build

# Skip tests for faster build
./gradlew clean build -x test

# Build output location
ls -lh build/libs/ai-assisted-trading-engine-1.0.0.jar
```

### 4. Database Migration

Database migrations run automatically on startup using Flyway. The application will:

1. Create the `journal_entries` table
2. Set up necessary indexes
3. Apply any pending migrations

To manually check migration status:

```bash
# Using Docker
docker exec -it trading_engine_db psql -U postgres -d trading_engine -c '\dt'

# Local PostgreSQL
psql -U postgres -d trading_engine -c '\dt'
```

---

## Configuration

### Application Profiles

The application supports multiple Spring profiles:

- **`dev`** (default) - Development mode with debug logging
- **`production`** - Production mode with optimized settings
- **`mock`** - Mock mode for testing without external APIs
- **`staging`** - Staging environment configuration

Set the profile in `.env`:

```bash
SPRING_PROFILES_ACTIVE=dev
```

### Trading Rules Configuration

Trading rules are configured in `src/main/resources/application.yml`:

```yaml
trading:
  # Risk Management
  min-risk-percent: 0.5
  max-risk-percent: 1.0
  default-risk-percent: 1.0
  min-risk-reward-ratio: 3.0
  max-daily-loss-r: 2.0

  # Session Rules
  london-session-enabled: true
  ny-session-enabled: true
  asia-session-enabled: false

  # Setup Quality
  allow-a-quality: true
  allow-b-quality: true
  allow-c-quality: false
```

### Rate Limiting

Binance API rate limiting is automatically enforced:

- **Weight limit**: 1200 per minute
- **Auto back-off**: On 418/429 errors
- **Monitoring**: Via `X-MBX-USED-WEIGHT` headers
- **Cache**: 10-second Redis cache reduces API calls

Configuration in `application.yml`:

```yaml
binance:
  cache-seconds: 10
  timeout-seconds: 10
```

---

## Running the Application

### Development Mode

```bash
# Using run script (recommended)
./scripts/run.sh

# With custom JVM options
JAVA_OPTS="-Xms1g -Xmx2g" ./scripts/run.sh

# Direct execution
java -jar build/libs/ai-assisted-trading-engine-1.0.0.jar
```

### Production Mode - Systemd Service

For 24/7 operation, install as a systemd service:

```bash
# Build first
./gradlew build

# Install service
sudo ./scripts/install-service.sh

# Configure API keys
sudo nano /etc/ai-trading-engine/environment

# Enable auto-start on boot
sudo systemctl enable ai-trading-engine

# Start service
sudo systemctl start ai-trading-engine

# Check status
sudo systemctl status ai-trading-engine

# View logs
sudo journalctl -u ai-trading-engine -f
```

See [scripts/README.md](scripts/README.md) for detailed service management.

### Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Restart application
docker-compose restart app

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

---

## Verification

### 1. Health Check

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

### 2. Test Webhook

```bash
curl -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-webhook-api-key" \
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

Expected response:

```json
{
  "success": true,
  "signalId": "...",
  "status": "VALID",
  "action": "Monitor per trading plan",
  "setupQuality": "A",
  "rulesPassed": true
}
```

### 3. Check Telegram Notifications

If the signal is valid, you should receive a Telegram notification with:
- Signal details
- Setup quality
- Position sizing recommendations
- Risk/reward analysis

### 4. Database Verification

```bash
# Check journal entries
docker exec -it trading_engine_db psql -U postgres -d trading_engine \
  -c "SELECT COUNT(*) FROM journal_entries;"

# View recent entries
docker exec -it trading_engine_db psql -U postgres -d trading_engine \
  -c "SELECT id, symbol, event, status, created_at FROM journal_entries ORDER BY created_at DESC LIMIT 5;"
```

---

## Troubleshooting

### Application Won't Start

**Issue**: "Failed to configure a DataSource"

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check connection
docker exec -it trading_engine_db psql -U postgres -c '\l'

# Verify .env configuration
grep DB_ .env
```

**Issue**: "Cannot connect to Redis"

```bash
# Check Redis is running
docker ps | grep redis

# Test connection
docker exec -it trading_engine_redis redis-cli ping
```

### API Errors

**Issue**: "401 Unauthorized" on webhook

```bash
# Verify WEBHOOK_API_KEY is set
grep WEBHOOK_API_KEY .env

# Test with correct key
curl -H "X-API-Key: $(grep WEBHOOK_API_KEY .env | cut -d= -f2)" \
  http://localhost:8080/api/webhook/health
```

**Issue**: Binance 418/429 rate limit errors

The application handles this automatically with exponential back-off. Check logs:

```bash
docker-compose logs app | grep -i "rate limit"
```

To reduce API calls, increase cache TTL in `application.yml`:

```yaml
binance:
  cache-seconds: 30  # Increase from 10 to 30
```

### Telegram Notifications Not Received

```bash
# Check configuration
grep TELEGRAM .env

# Verify bot token
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getMe"

# Check application logs
docker-compose logs app | grep -i telegram
```

### Database Migration Failures

```bash
# Check migration status
docker exec -it trading_engine_db psql -U postgres -d trading_engine \
  -c "SELECT * FROM flyway_schema_history;"

# Manual rollback (if needed)
docker exec -it trading_engine_db psql -U postgres -d trading_engine \
  -c "DROP TABLE IF EXISTS journal_entries CASCADE;"

# Restart application to retry migrations
docker-compose restart app
```

### Port Already in Use

```bash
# Find process using port 8080
sudo lsof -i :8080

# Kill process
sudo kill -9 <PID>

# Or use different port
echo "SERVER_PORT=8081" >> .env
```

### Gradle Build Failures

```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Check Java version
java -version  # Must be Java 21

# Update wrapper
./gradlew wrapper --gradle-version 8.14.3
```

### View Application Logs

```bash
# Docker Compose
docker-compose logs -f app

# Systemd service
sudo journalctl -u ai-trading-engine -f

# Direct run
# Logs output to console
```

---

## Next Steps

1. **Configure TradingView Webhooks**: See [docs/tradingview/](docs/tradingview/) for Pine Script strategies
2. **Review API Documentation**: See [docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md)
3. **Set Up Monitoring**: Configure logging and alerting for production
4. **Test Strategies**: Use mock mode to test without real trades
5. **Production Deployment**: Follow [scripts/README.md](scripts/README.md) for systemd setup

---

## Additional Resources

- **API Documentation**: [docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md)
- **Service Deployment**: [scripts/README.md](scripts/README.md)
- **TradingView Integration**: [docs/tradingview/](docs/tradingview/)
- **Development Guide**: [DEVELOPMENT.md](DEVELOPMENT.md)

---

## Support

For issues, questions, or contributions:

1. Check this setup guide
2. Review [DEVELOPMENT.md](DEVELOPMENT.md) for development setup
3. Check application logs for error messages
4. Review relevant documentation in `docs/`

---

**Happy Trading! 🚀**

# Scripts Directory

This directory contains scripts for running and managing the AI Trading Engine.

## 🚀 Quick Start - Simple Run

### `run.sh` - Run the Application

**Easiest way to run the application.**

```bash
# Build first
./gradlew build

# Run
./scripts/run.sh
```

**Features:**
- ✅ Automatically finds the JAR file
- ✅ Loads environment variables from `.env` file
- ✅ Configurable JVM options
- ✅ Simple Ctrl+C to stop

**Setup:**

Create a `.env` file in the project root:

```bash
# Copy template
cp scripts/environment.template .env

# Edit with your API keys
nano .env
```

**Examples:**

```bash
# Default run
./scripts/run.sh

# Custom JVM options
JAVA_OPTS="-Xms1g -Xmx2g" ./scripts/run.sh

# Mock mode (no API keys needed)
SPRING_PROFILES_ACTIVE=mock ./scripts/run.sh
```

---

## 🔧 Production Deployment - Systemd Service

For 24/7 production operation, install as a systemd service.

### `install-service.sh` - Install as System Service

```bash
# Build and install
./gradlew build
sudo ./scripts/install-service.sh
```

**What it does:**
- Creates dedicated `trading` user
- Installs to `/opt/ai-trading-engine/`
- Sets up configuration in `/etc/ai-trading-engine/`
- Creates systemd service with auto-restart
- Installs global management commands

**After installation:**

```bash
# Configure API keys
sudo nano /etc/ai-trading-engine/environment

# Enable auto-start on boot
sudo systemctl enable ai-trading-engine

# Start service
sudo systemctl start ai-trading-engine
# Or use global command:
sudo trading-start

# Check status
sudo systemctl status ai-trading-engine
# Or:
sudo trading-status

# View logs
sudo journalctl -u ai-trading-engine -f
# Or:
sudo trading-logs

# Stop service
sudo systemctl stop ai-trading-engine
# Or:
sudo trading-stop

# Restart service
sudo systemctl restart ai-trading-engine
# Or:
sudo trading-restart
```

### `uninstall-service.sh` - Remove Service

```bash
sudo ./scripts/uninstall-service.sh
```

Removes the service and optionally cleans up configuration and logs.

---

## 📁 Configuration Files

### `environment.template`

Template for environment variables. Copy to:
- `.env` (for simple run with `run.sh`)
- `/etc/ai-trading-engine/environment` (for systemd service)

### `ai-trading-engine.service`

Systemd service file with:
- Auto-restart on failure
- Resource limits (1GB heap)
- Security hardening
- Systemd journal logging

---

## 📊 Comparison

| | Simple Run | Systemd Service |
|------------|-----------|-----------------|
| **Start** | `./scripts/run.sh` | `sudo systemctl start ai-trading-engine` |
| **Stop** | `Ctrl+C` | `sudo systemctl stop ai-trading-engine` |
| **Logs** | Terminal | `sudo journalctl -u ai-trading-engine -f` |
| **Auto-restart** | ❌ No | ✅ Yes |
| **Boot startup** | ❌ No | ✅ Yes (if enabled) |
| **Installation** | None | `sudo ./scripts/install-service.sh` |
| **Best for** | Development | Production |

---

## 🔑 Environment Variables

### Required
- `WEBHOOK_API_KEY` - Webhook authentication
- `CLAUDE_API_KEY` - Claude AI API key
- `BINANCE_API_KEY` - Binance API key
- `BINANCE_API_SECRET` - Binance secret
- `TELEGRAM_BOT_TOKEN` - Telegram bot token
- `TELEGRAM_CHAT_ID` - Telegram chat ID

### Optional
- `DB_PASSWORD` - PostgreSQL password
- `REDIS_PASSWORD` - Redis password
- `ACCOUNT_BALANCE` - Account size (default: 10000)
- `SPRING_PROFILES_ACTIVE` - Spring profile (production/mock/dev)
- `JAVA_OPTS` - JVM options

---

## 🛠️ Troubleshooting

### JAR not found
```bash
./gradlew build
ls -lh build/libs/ai-assisted-trading-engine-1.0.0.jar
```

### Environment not loaded
```bash
# Create .env file
cp scripts/environment.template .env
nano .env
```

### Permission denied
```bash
chmod +x scripts/*.sh
```

### Service won't start
```bash
sudo systemctl status ai-trading-engine
sudo journalctl -u ai-trading-engine -n 50
```

---

## 💡 Quick Workflows

**Development:**
```bash
# Code → Build → Run
./gradlew build && ./scripts/run.sh
```

**Mock mode testing:**
```bash
SPRING_PROFILES_ACTIVE=mock ./scripts/run.sh
```

**Production update:**
```bash
./gradlew build
sudo systemctl stop ai-trading-engine
sudo cp build/libs/ai-assisted-trading-engine-1.0.0.jar \
    /opt/ai-trading-engine/ai-assisted-trading-engine.jar
sudo systemctl start ai-trading-engine
```

---

**See also:** [Complete Deployment Guide](../docs/SERVICE_DEPLOYMENT.md)

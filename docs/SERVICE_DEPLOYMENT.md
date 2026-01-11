# Linux Service Deployment Guide

This guide covers deploying the AI-Assisted Trading Engine as a systemd service on Linux servers.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Installation](#quick-installation)
3. [Manual Installation](#manual-installation)
4. [Service Management](#service-management)
5. [Configuration](#configuration)
6. [Monitoring](#monitoring)
7. [Troubleshooting](#troubleshooting)
8. [Uninstallation](#uninstallation)

## Prerequisites

### System Requirements

- **OS:** Ubuntu 20.04+, Debian 11+, RHEL 8+, or any systemd-based Linux
- **Java:** OpenJDK 21 or later
- **PostgreSQL:** 14+ (running and accessible)
- **Redis:** 6+ (running and accessible)
- **User:** Root or sudo access for installation

### Install Java 21

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk

# RHEL/CentOS/Fedora
sudo dnf install java-21-openjdk

# Verify installation
java -version
```

### Install PostgreSQL and Redis

```bash
# Ubuntu/Debian
sudo apt install postgresql redis-server

# RHEL/CentOS/Fedora
sudo dnf install postgresql-server redis

# Start services
sudo systemctl enable postgresql redis
sudo systemctl start postgresql redis
```

## Quick Installation

### 1. Build the Application

```bash
# Navigate to project directory
cd ai-assisted-trading-engine

# Build JAR file
./gradlew build

# Verify JAR was created
ls -lh build/libs/ai-assisted-trading-engine-1.0.0.jar
```

### 2. Run Installation Script

```bash
# Make installation script executable
chmod +x scripts/install-service.sh

# Run installation (requires sudo)
sudo ./scripts/install-service.sh
```

The installation script will:
- Create a dedicated `trading` user
- Install application to `/opt/ai-trading-engine`
- Create configuration directory at `/etc/ai-trading-engine`
- Install systemd service
- Create management commands

### 3. Configure API Keys

```bash
# Edit configuration file
sudo nano /etc/ai-trading-engine/environment
```

Update the following variables:
```bash
WEBHOOK_API_KEY=your-secure-webhook-api-key
CLAUDE_API_KEY=your-claude-api-key
BINANCE_API_KEY=your-binance-api-key
BINANCE_API_SECRET=your-binance-api-secret
TELEGRAM_BOT_TOKEN=your-telegram-bot-token
TELEGRAM_CHAT_ID=your-telegram-chat-id
DB_PASSWORD=your-postgresql-password
ACCOUNT_BALANCE=10000
```

**Security Note:** The environment file is protected with 600 permissions (readable only by owner).

### 4. Enable and Start Service

```bash
# Enable service to start on boot
sudo systemctl enable ai-trading-engine

# Start the service
sudo trading-start

# Or manually:
sudo systemctl start ai-trading-engine
```

## Manual Installation

If you prefer manual installation without the script:

### 1. Create Service User

```bash
sudo useradd -r -s /bin/false trading
```

### 2. Create Directories

```bash
sudo mkdir -p /opt/ai-trading-engine
sudo mkdir -p /opt/ai-trading-engine/logs
sudo mkdir -p /etc/ai-trading-engine
sudo mkdir -p /var/log/ai-trading-engine
```

### 3. Copy Application Files

```bash
# Copy JAR file
sudo cp build/libs/ai-assisted-trading-engine-1.0.0.jar \
    /opt/ai-trading-engine/ai-assisted-trading-engine.jar

# Copy environment template
sudo cp scripts/environment.template \
    /etc/ai-trading-engine/environment

# Set permissions
sudo chmod 600 /etc/ai-trading-engine/environment
```

### 4. Configure Environment

```bash
sudo nano /etc/ai-trading-engine/environment
```

### 5. Set Ownership

```bash
sudo chown -R trading:trading /opt/ai-trading-engine
sudo chown -R trading:trading /var/log/ai-trading-engine
sudo chown -R trading:trading /etc/ai-trading-engine
```

### 6. Install Systemd Service

```bash
# Copy service file
sudo cp scripts/ai-trading-engine.service /etc/systemd/system/

# Reload systemd
sudo systemctl daemon-reload

# Enable service
sudo systemctl enable ai-trading-engine
```

## Service Management

### Using Management Commands (Recommended)

After installation, these commands are available:

```bash
# Start service
sudo trading-start

# Stop service
sudo trading-stop

# Restart service
sudo trading-restart

# Check status
sudo trading-status

# View live logs
sudo trading-logs
```

### Using Systemd Directly

```bash
# Start
sudo systemctl start ai-trading-engine

# Stop
sudo systemctl stop ai-trading-engine

# Restart
sudo systemctl restart ai-trading-engine

# Status
sudo systemctl status ai-trading-engine

# Enable auto-start on boot
sudo systemctl enable ai-trading-engine

# Disable auto-start on boot
sudo systemctl disable ai-trading-engine
```

### Using Scripts Directly

```bash
# From scripts directory
cd scripts

# Make scripts executable
chmod +x *.sh

# Start service
./start.sh

# Stop service
./stop.sh

# Check status
./status.sh

# Restart service
./restart.sh

# View logs
./logs.sh
```

## Configuration

### Environment Variables

All configuration is managed via `/etc/ai-trading-engine/environment`:

```bash
# API Keys
WEBHOOK_API_KEY=              # Webhook authentication
CLAUDE_API_KEY=               # Claude AI API key
BINANCE_API_KEY=              # Binance Futures API key
BINANCE_API_SECRET=           # Binance API secret
TELEGRAM_BOT_TOKEN=           # Telegram bot token
TELEGRAM_CHAT_ID=             # Telegram chat ID

# Database
DB_PASSWORD=                  # PostgreSQL password

# Trading
ACCOUNT_BALANCE=10000         # Account size for calculations

# Optional
REDIS_PASSWORD=               # Redis password (if protected)
SPRING_PROFILES_ACTIVE=production  # Spring profile
JAVA_OPTS=-Xms512m -Xmx1024m  # JVM options
```

### Application Configuration

Edit `/opt/ai-trading-engine/application.yml` for advanced settings:

```bash
sudo nano /opt/ai-trading-engine/application.yml
```

**Note:** Most configuration should be in environment variables for security.

### Resource Limits

The service is configured with:
- **Memory:** 512MB min, 1GB max (adjustable via JAVA_OPTS)
- **Open Files:** 65,536
- **Processes:** 4,096

To modify, edit `/etc/systemd/system/ai-trading-engine.service`:

```bash
sudo nano /etc/systemd/system/ai-trading-engine.service
sudo systemctl daemon-reload
sudo systemctl restart ai-trading-engine
```

## Monitoring

### Check Service Health

```bash
# Service status
sudo trading-status

# Or
sudo systemctl status ai-trading-engine
```

### View Logs

```bash
# Live logs (follow mode)
sudo trading-logs

# Or
sudo journalctl -u ai-trading-engine -f

# Last 100 lines
sudo journalctl -u ai-trading-engine -n 100

# Logs since today
sudo journalctl -u ai-trading-engine --since today

# Logs between time range
sudo journalctl -u ai-trading-engine --since "2024-01-10 09:00" --until "2024-01-10 17:00"
```

### Monitor Resource Usage

```bash
# CPU and memory usage
sudo systemctl status ai-trading-engine

# Detailed resource info
sudo systemd-cgtop

# Process tree
sudo ps aux | grep ai-trading-engine
```

### Check Application Health

```bash
# HTTP health endpoint (if service is running)
curl http://localhost:8080/api/webhook/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "AI-Assisted Trading Engine"
}
```

## Troubleshooting

### Service Won't Start

1. **Check logs:**
```bash
sudo journalctl -u ai-trading-engine -n 50
```

2. **Verify JAR file exists:**
```bash
ls -lh /opt/ai-trading-engine/ai-assisted-trading-engine.jar
```

3. **Check permissions:**
```bash
sudo ls -la /opt/ai-trading-engine
sudo ls -la /etc/ai-trading-engine
```

4. **Verify Java installation:**
```bash
java -version
/usr/bin/java -version
```

5. **Check configuration:**
```bash
sudo cat /etc/ai-trading-engine/environment
```

### Database Connection Issues

1. **Verify PostgreSQL is running:**
```bash
sudo systemctl status postgresql
```

2. **Test database connection:**
```bash
psql -U postgres -d trading_engine -c "SELECT 1;"
```

3. **Check database credentials in environment file**

### Memory Issues

If service crashes with OutOfMemoryError:

1. **Increase heap size in service file:**
```bash
sudo nano /etc/systemd/system/ai-trading-engine.service
```

Change:
```
-Xms512m -Xmx1024m
```

To:
```
-Xms1024m -Xmx2048m
```

2. **Reload and restart:**
```bash
sudo systemctl daemon-reload
sudo systemctl restart ai-trading-engine
```

### Port Already in Use

If port 8080 is already in use:

1. **Find process using port:**
```bash
sudo lsof -i :8080
```

2. **Change application port:**

Edit environment file:
```bash
sudo nano /etc/ai-trading-engine/environment
```

Add:
```bash
SERVER_PORT=8081
```

### Permission Denied Errors

1. **Fix ownership:**
```bash
sudo chown -R trading:trading /opt/ai-trading-engine
sudo chown -R trading:trading /var/log/ai-trading-engine
sudo chown -R trading:trading /etc/ai-trading-engine
```

2. **Fix file permissions:**
```bash
sudo chmod 755 /opt/ai-trading-engine
sudo chmod 644 /opt/ai-trading-engine/ai-assisted-trading-engine.jar
sudo chmod 600 /etc/ai-trading-engine/environment
```

## Updating the Application

### Update Process

1. **Build new version:**
```bash
cd ai-assisted-trading-engine
./gradlew build
```

2. **Stop service:**
```bash
sudo trading-stop
```

3. **Backup current JAR:**
```bash
sudo cp /opt/ai-trading-engine/ai-assisted-trading-engine.jar \
    /opt/ai-trading-engine/ai-assisted-trading-engine.jar.backup
```

4. **Copy new JAR:**
```bash
sudo cp build/libs/ai-assisted-trading-engine-1.0.0.jar \
    /opt/ai-trading-engine/ai-assisted-trading-engine.jar
```

5. **Set permissions:**
```bash
sudo chown trading:trading /opt/ai-trading-engine/ai-assisted-trading-engine.jar
```

6. **Start service:**
```bash
sudo trading-start
```

7. **Verify:**
```bash
sudo trading-logs
```

### Rollback if Needed

```bash
sudo trading-stop
sudo cp /opt/ai-trading-engine/ai-assisted-trading-engine.jar.backup \
    /opt/ai-trading-engine/ai-assisted-trading-engine.jar
sudo trading-start
```

## Uninstallation

### Using Uninstall Script

```bash
# Make script executable
chmod +x scripts/uninstall-service.sh

# Run uninstallation
sudo ./scripts/uninstall-service.sh
```

The script will:
- Stop and disable service
- Remove systemd service file
- Remove management commands
- Optionally remove: application directory, configuration, logs, service user

### Manual Uninstallation

```bash
# Stop and disable service
sudo systemctl stop ai-trading-engine
sudo systemctl disable ai-trading-engine

# Remove service file
sudo rm /etc/systemd/system/ai-trading-engine.service
sudo systemctl daemon-reload

# Remove management commands
sudo rm /usr/local/bin/trading-*

# Remove application
sudo rm -rf /opt/ai-trading-engine

# Remove configuration (contains API keys - be careful!)
sudo rm -rf /etc/ai-trading-engine

# Remove logs
sudo rm -rf /var/log/ai-trading-engine

# Remove service user
sudo userdel trading
```

## Security Best Practices

1. **Protect Environment File:**
```bash
# Ensure only root can read
sudo chmod 600 /etc/ai-trading-engine/environment
```

2. **Use Firewall:**
```bash
# Allow only necessary connections
sudo ufw allow 8080/tcp  # Application port
sudo ufw enable
```

3. **Regular Updates:**
```bash
# Keep system updated
sudo apt update && sudo apt upgrade
```

4. **Monitor Logs:**
```bash
# Check for suspicious activity
sudo journalctl -u ai-trading-engine | grep -i error
```

5. **Backup Configuration:**
```bash
# Regular backups
sudo tar -czf trading-config-backup-$(date +%Y%m%d).tar.gz \
    /etc/ai-trading-engine
```

## Support

For issues or questions:
- Check logs: `sudo trading-logs`
- Review troubleshooting section above
- Check project README: `/opt/ai-trading-engine/README.md`
- Review GitHub issues

---

**Remember:** This is a production-grade trading system. Always:
- Test configuration changes in staging first
- Monitor logs after updates
- Keep backups of configuration and data
- Secure your API keys

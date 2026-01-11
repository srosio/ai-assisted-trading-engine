#!/bin/bash
set -e

# AI Trading Engine Service Installation Script
# This script installs the trading engine as a systemd service

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SERVICE_NAME="ai-trading-engine"
SERVICE_USER="trading"
INSTALL_DIR="/opt/ai-trading-engine"
CONFIG_DIR="/etc/ai-trading-engine"
LOG_DIR="/var/log/ai-trading-engine"

echo -e "${GREEN}AI Trading Engine Service Installation${NC}"
echo "========================================"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run as root${NC}"
    echo "Please run: sudo ./install-service.sh"
    exit 1
fi

# Check if JAR file exists
if [ ! -f "build/libs/ai-assisted-trading-engine-1.0.0.jar" ]; then
    echo -e "${RED}Error: JAR file not found${NC}"
    echo "Please build the project first with: ./gradlew build"
    exit 1
fi

echo -e "${YELLOW}Step 1: Creating service user${NC}"
if id "$SERVICE_USER" &>/dev/null; then
    echo "User $SERVICE_USER already exists"
else
    useradd -r -s /bin/false $SERVICE_USER
    echo "Created user: $SERVICE_USER"
fi

echo ""
echo -e "${YELLOW}Step 2: Creating directories${NC}"
mkdir -p $INSTALL_DIR
mkdir -p $INSTALL_DIR/logs
mkdir -p $CONFIG_DIR
mkdir -p $LOG_DIR

echo "Created directories:"
echo "  - $INSTALL_DIR"
echo "  - $CONFIG_DIR"
echo "  - $LOG_DIR"

echo ""
echo -e "${YELLOW}Step 3: Copying application files${NC}"
cp build/libs/ai-assisted-trading-engine-1.0.0.jar $INSTALL_DIR/ai-assisted-trading-engine.jar
echo "Copied JAR file to $INSTALL_DIR"

# Copy configuration template
if [ ! -f "$CONFIG_DIR/environment" ]; then
    cp scripts/environment.template $CONFIG_DIR/environment
    chmod 600 $CONFIG_DIR/environment
    echo "Created environment file: $CONFIG_DIR/environment"
    echo -e "${YELLOW}WARNING: Please edit $CONFIG_DIR/environment with your API keys${NC}"
else
    echo "Environment file already exists: $CONFIG_DIR/environment"
fi

echo ""
echo -e "${YELLOW}Step 4: Setting permissions${NC}"
chown -R $SERVICE_USER:$SERVICE_USER $INSTALL_DIR
chown -R $SERVICE_USER:$SERVICE_USER $LOG_DIR
chown -R $SERVICE_USER:$SERVICE_USER $CONFIG_DIR
chmod 755 $INSTALL_DIR
chmod 644 $INSTALL_DIR/ai-assisted-trading-engine.jar
echo "Set ownership to $SERVICE_USER:$SERVICE_USER"

echo ""
echo -e "${YELLOW}Step 5: Installing systemd service${NC}"
cp scripts/ai-trading-engine.service /etc/systemd/system/
chmod 644 /etc/systemd/system/ai-trading-engine.service
systemctl daemon-reload
echo "Installed service file"

echo ""
echo -e "${YELLOW}Step 6: Creating management scripts${NC}"
cat > /usr/local/bin/trading-start <<'EOF'
#!/bin/bash
systemctl start ai-trading-engine
systemctl status ai-trading-engine --no-pager
EOF

cat > /usr/local/bin/trading-stop <<'EOF'
#!/bin/bash
systemctl stop ai-trading-engine
echo "Service stopped"
EOF

cat > /usr/local/bin/trading-restart <<'EOF'
#!/bin/bash
systemctl restart ai-trading-engine
systemctl status ai-trading-engine --no-pager
EOF

cat > /usr/local/bin/trading-status <<'EOF'
#!/bin/bash
systemctl status ai-trading-engine --no-pager
EOF

cat > /usr/local/bin/trading-logs <<'EOF'
#!/bin/bash
journalctl -u ai-trading-engine -f
EOF

chmod +x /usr/local/bin/trading-start
chmod +x /usr/local/bin/trading-stop
chmod +x /usr/local/bin/trading-restart
chmod +x /usr/local/bin/trading-status
chmod +x /usr/local/bin/trading-logs

echo "Created management commands:"
echo "  - trading-start   : Start the service"
echo "  - trading-stop    : Stop the service"
echo "  - trading-restart : Restart the service"
echo "  - trading-status  : Check service status"
echo "  - trading-logs    : View live logs"

echo ""
echo -e "${GREEN}Installation completed successfully!${NC}"
echo ""
echo "Next steps:"
echo "1. Edit configuration: sudo nano $CONFIG_DIR/environment"
echo "2. Enable service: sudo systemctl enable ai-trading-engine"
echo "3. Start service: sudo trading-start"
echo "4. View logs: sudo trading-logs"
echo ""
echo -e "${YELLOW}IMPORTANT: Configure your API keys in $CONFIG_DIR/environment before starting${NC}"

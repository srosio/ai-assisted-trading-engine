#!/bin/bash
set -e

# AI Trading Engine Service Uninstallation Script

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

echo -e "${YELLOW}AI Trading Engine Service Uninstallation${NC}"
echo "=========================================="
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run as root${NC}"
    echo "Please run: sudo ./uninstall-service.sh"
    exit 1
fi

# Confirm uninstallation
read -p "Are you sure you want to uninstall the service? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Uninstallation cancelled"
    exit 0
fi

echo ""
echo -e "${YELLOW}Step 1: Stopping and disabling service${NC}"
if systemctl is-active --quiet $SERVICE_NAME; then
    systemctl stop $SERVICE_NAME
    echo "Service stopped"
fi

if systemctl is-enabled --quiet $SERVICE_NAME; then
    systemctl disable $SERVICE_NAME
    echo "Service disabled"
fi

echo ""
echo -e "${YELLOW}Step 2: Removing systemd service file${NC}"
if [ -f "/etc/systemd/system/$SERVICE_NAME.service" ]; then
    rm /etc/systemd/system/$SERVICE_NAME.service
    systemctl daemon-reload
    echo "Removed service file"
fi

echo ""
echo -e "${YELLOW}Step 3: Removing management scripts${NC}"
rm -f /usr/local/bin/trading-start
rm -f /usr/local/bin/trading-stop
rm -f /usr/local/bin/trading-restart
rm -f /usr/local/bin/trading-status
rm -f /usr/local/bin/trading-logs
echo "Removed management scripts"

echo ""
echo -e "${YELLOW}Step 4: Removing application directory${NC}"
if [ -d "$INSTALL_DIR" ]; then
    rm -rf $INSTALL_DIR
    echo "Removed $INSTALL_DIR"
fi

echo ""
echo -e "${YELLOW}Step 5: Handling configuration and logs${NC}"
read -p "Remove configuration directory ($CONFIG_DIR)? (yes/no): " remove_config
if [ "$remove_config" == "yes" ]; then
    rm -rf $CONFIG_DIR
    echo "Removed $CONFIG_DIR"
else
    echo "Kept $CONFIG_DIR (contains your API keys)"
fi

read -p "Remove log directory ($LOG_DIR)? (yes/no): " remove_logs
if [ "$remove_logs" == "yes" ]; then
    rm -rf $LOG_DIR
    echo "Removed $LOG_DIR"
else
    echo "Kept $LOG_DIR (contains historical logs)"
fi

echo ""
echo -e "${YELLOW}Step 6: Removing service user${NC}"
read -p "Remove service user '$SERVICE_USER'? (yes/no): " remove_user
if [ "$remove_user" == "yes" ]; then
    if id "$SERVICE_USER" &>/dev/null; then
        userdel $SERVICE_USER
        echo "Removed user: $SERVICE_USER"
    fi
else
    echo "Kept user: $SERVICE_USER"
fi

echo ""
echo -e "${GREEN}Uninstallation completed!${NC}"

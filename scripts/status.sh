#!/bin/bash
# Check AI Trading Engine Service Status

SERVICE_NAME="ai-trading-engine"

# Check if systemd service is installed
if systemctl list-unit-files | grep -q "$SERVICE_NAME.service"; then
    sudo systemctl status $SERVICE_NAME --no-pager
else
    echo "Service not installed. Please run: sudo ./install-service.sh"
    exit 1
fi

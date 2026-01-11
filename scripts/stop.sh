#!/bin/bash
# Stop AI Trading Engine Service

SERVICE_NAME="ai-trading-engine"

# Check if systemd service is installed
if systemctl list-unit-files | grep -q "$SERVICE_NAME.service"; then
    echo "Stopping $SERVICE_NAME..."
    sudo systemctl stop $SERVICE_NAME
    echo "Service stopped successfully"
else
    echo "Service not installed. Please run: sudo ./install-service.sh"
    exit 1
fi

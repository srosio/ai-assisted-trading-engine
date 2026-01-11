#!/bin/bash
# Start AI Trading Engine Service

SERVICE_NAME="ai-trading-engine"

# Check if systemd service is installed
if systemctl list-unit-files | grep -q "$SERVICE_NAME.service"; then
    echo "Starting $SERVICE_NAME via systemd..."
    sudo systemctl start $SERVICE_NAME
    sudo systemctl status $SERVICE_NAME --no-pager
else
    echo "Service not installed. Please run: sudo ./install-service.sh"
    exit 1
fi

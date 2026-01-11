#!/bin/bash
# View AI Trading Engine Service Logs

SERVICE_NAME="ai-trading-engine"

# Check if systemd service is installed
if systemctl list-unit-files | grep -q "$SERVICE_NAME.service"; then
    echo "Viewing logs for $SERVICE_NAME (Ctrl+C to exit)..."
    sudo journalctl -u $SERVICE_NAME -f
else
    echo "Service not installed. Please run: sudo ./install-service.sh"
    exit 1
fi

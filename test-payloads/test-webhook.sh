#!/bin/bash

# Test script for Universal Trading Event Schema
# Tests both nested (recommended) and flat formats

API_URL="${API_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-your-api-key-here}"

echo "=========================================="
echo "Universal Trading Event Schema - Test Script"
echo "=========================================="
echo ""
echo "API URL: $API_URL"
echo "Endpoint: /api/webhook/test"
echo ""

# Test 1: Nested Format (Recommended)
echo "Test 1: Nested Format (Recommended)"
echo "-----------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Candle 2 Closure",
  "event_type": "reversal",
  "direction": "bullish",
  "session": "London",
  "price": {
    "close": 92100.50,
    "stop_loss": 91800.00
  },
  "context": {
    "swept_level": 91950.00,
    "htf_bias": "bullish",
    "displacement": true
  },
  "timestamp": "2026-01-14T10:30:00Z"
}' | jq '.'

echo ""
echo ""

# Test 2: Flat Format (Alternative)
echo "Test 2: Flat Format (Alternative)"
echo "----------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "ETHUSDT",
  "timeframe": "5",
  "strategy": "Liquidity Sweeps",
  "event_type": "sweep",
  "direction": "bearish",
  "session": "NY",
  "currentPrice": 2050.75,
  "suggestedStopLoss": 2045.00,
  "sweptLevel": 2052.00,
  "htfBias": "bearish",
  "displacement": true,
  "volumeSpike": true
}' | jq '.'

echo ""
echo ""

# Test 3: Legacy Format (Backward Compatible)
echo "Test 3: Legacy Format (Backward Compatible)"
echo "--------------------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "liquidity_sweep_long",
  "session": "London",
  "price": 43120.50,
  "previousDayHigh": 43210.00,
  "previousDayLow": 42880.00,
  "volumeSpike": true,
  "displacementDetected": true,
  "htfBias": "bullish",
  "sweptHigh": 43150.00
}' | jq '.'

echo ""
echo ""
echo "=========================================="
echo "Tests Complete!"
echo "=========================================="

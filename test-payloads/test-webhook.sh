#!/bin/bash

# Test script for Universal Trading Event Schema
# Tests nested format only

API_URL="${API_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-your-api-key-here}"

echo "=========================================="
echo "Universal Trading Event Schema - Test Script"
echo "=========================================="
echo ""
echo "API URL: $API_URL"
echo "Endpoint: /api/webhook/test"
echo ""

# Test 1: Candle 2 Closure - Reversal
echo "Test 1: Candle 2 Closure - Bullish Reversal"
echo "-------------------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Candle 2 Closure",
  "eventType": "reversal",
  "direction": "bullish",
  "session": "London",
  "price": {
    "close": 92100.50,
    "stopLoss": 91800.00
  },
  "context": {
    "htfBias": "bullish",
    "displacement": true
  }
}' | jq '.'

echo ""
echo ""

# Test 2: Liquidity Sweep
echo "Test 2: Liquidity Sweeps - Bearish"
echo "-----------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "ETHUSDT",
  "timeframe": "5",
  "strategy": "Liquidity Sweeps",
  "eventType": "sweep",
  "direction": "bearish",
  "session": "NY",
  "price": {
    "close": 2050.75,
    "stopLoss": 2065.00
  },
  "context": {
    "sweptLevel": 2052.00,
    "htfBias": "bearish",
    "displacement": true,
    "volumeSpike": true
  }
}' | jq '.'

echo ""
echo ""

# Test 3: Minimal Required Fields
echo "Test 3: Minimal Required Fields"
echo "--------------------------------"
curl -X POST "$API_URL/api/webhook/test" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
  "symbol": "SOLUSDT",
  "timeframe": "15",
  "strategy": "Simple Reversal",
  "eventType": "reversal",
  "direction": "bullish",
  "session": "London",
  "price": {
    "close": 105.50
  }
}' | jq '.'

echo ""
echo ""
echo "=========================================="
echo "Tests Complete!"
echo "=========================================="

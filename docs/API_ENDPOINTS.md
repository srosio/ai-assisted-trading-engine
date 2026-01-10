# API Endpoints Documentation

## Webhook Endpoints

### POST /api/webhook/tradingview

Receive TradingView webhook with market event.

**Authentication:** Required via `X-API-Key` header or `apiKey` query parameter

**Request Headers:**
```
Content-Type: application/json
X-API-Key: your-secret-api-key
```

**Request Body (camelCase):**
```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "liquidity_sweep_long",
  "session": "NY",
  "price": 43120.50,
  "previousDayHigh": 43210.00,
  "previousDayLow": 42880.00,
  "volumeSpike": true,
  "displacementDetected": true,
  "htfBias": "bullish"
}
```

**Response (camelCase):**
```json
{
  "success": true,
  "signalId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "VALID",
  "action": "Monitor per trading plan",
  "setupQuality": "A",
  "rulesPassed": true
}
```

**Status Codes:**
- 200: Success
- 401: Unauthorized (invalid or missing API key)
- 400: Invalid request (validation error)
- 500: Server error

**Important:** Telegram notifications are sent ONLY when status is `VALID`. All signals are journaled regardless.

---

### GET /api/webhook/health

Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "service": "AI-Assisted Trading Engine"
}
```

---

### POST /api/webhook/test

Test endpoint for manual signal testing (development only).

**Request Body:** Same as /tradingview endpoint

**Response:** Full TradeSignal object with all details

---

## Example cURL Commands

### Send Test Signal (with API Key)
```bash
curl -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key" \
  -d '{
    "symbol": "BTCUSDT",
    "timeframe": "5m",
    "event": "liquidity_sweep_long",
    "session": "NY",
    "price": 43120.50,
    "previousDayHigh": 43210.00,
    "previousDayLow": 42880.00,
    "volumeSpike": true,
    "htfBias": "bullish"
  }'
```

### Alternative: Using Query Parameter
```bash
curl -X POST "http://localhost:8080/api/webhook/tradingview?apiKey=your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "timeframe": "5m",
    "event": "liquidity_sweep_long",
    "session": "NY",
    "price": 43120.50,
    "previousDayHigh": 43210.00,
    "previousDayLow": 42880.00,
    "volumeSpike": true,
    "htfBias": "bullish"
  }'
```

### Health Check (no authentication)
```bash
curl http://localhost:8080/api/webhook/health
```

---

## Response Status Values

### Signal Status
- `VALID` - All rules passed, setup is valid for consideration
- `INVALID` - One or more rules failed, setup is blocked

### Setup Quality
- `A` - High quality setup
- `B` - Medium quality setup
- `C` - Low quality setup (typically blocked)

### Trade Direction
- `LONG` - Buy/long position
- `SHORT` - Sell/short position

---

## Error Handling

All errors return a standard error response:

```json
{
  "success": false,
  "error": "Error description here"
}
```

Common errors:
- Missing required fields
- Invalid symbol format
- Database connection issues
- External API failures (Binance, Claude)

---

## Rate Limiting

Consider implementing rate limiting based on your needs:
- Development: Unlimited
- Production: 60 requests/minute recommended

---

## Security

### API Key Authentication (Implemented)

All webhook endpoints (except `/health`) require API key authentication via Spring Security:

**Configuration:**
Set the `WEBHOOK_API_KEY` environment variable:
```bash
export WEBHOOK_API_KEY=your-secure-random-key
```

**Two Authentication Methods:**

1. **HTTP Header (Recommended):**
```bash
curl -H "X-API-Key: your-secret-api-key" ...
```

2. **Query Parameter:**
```bash
curl "http://localhost:8080/api/webhook/tradingview?apiKey=your-secret-api-key" ...
```

**Security Features:**
- ✅ Stateless authentication (no sessions)
- ✅ Spring Security integration
- ✅ All endpoints protected except health check
- ✅ 401 Unauthorized response for invalid keys

**Best Practices:**
1. Use strong, random API keys (min 32 characters)
2. Rotate keys periodically
3. Never commit keys to git
4. Use HTTPS in production
5. Store keys in environment variables only

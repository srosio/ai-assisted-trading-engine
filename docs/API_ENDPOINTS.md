# API Endpoints Documentation

## Webhook Endpoints

### POST /api/webhook/tradingview

Receive TradingView webhook with market event.

**Request Body:**
```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "liquidity_sweep_long",
  "session": "NY",
  "price": 43120.50,
  "previous_day_high": 43210.00,
  "previous_day_low": 42880.00,
  "volume_spike": true,
  "displacement_detected": true,
  "htf_bias": "bullish"
}
```

**Response:**
```json
{
  "success": true,
  "signal_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "VALID",
  "action": "Monitor per trading plan",
  "setup_quality": "A",
  "rules_passed": true
}
```

**Status Codes:**
- 200: Success
- 400: Invalid request (validation error)
- 500: Server error

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

### Send Test Signal
```bash
curl -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "timeframe": "5m",
    "event": "liquidity_sweep_long",
    "session": "NY",
    "price": 43120.50,
    "previous_day_high": 43210.00,
    "previous_day_low": 42880.00,
    "volume_spike": true,
    "htf_bias": "bullish"
  }'
```

### Health Check
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

### Webhook Signature Verification (Recommended)

To secure the webhook endpoint, implement signature verification:

1. Generate a secret key
2. Have TradingView sign the payload with the secret
3. Verify the signature on each request

Example:
```java
@PostMapping("/tradingview")
public ResponseEntity<?> receiveTradingViewWebhook(
        @RequestBody TradingViewWebhook webhook,
        @RequestHeader("X-Webhook-Signature") String signature) {

    if (!verifySignature(webhook, signature)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // Process webhook...
}
```

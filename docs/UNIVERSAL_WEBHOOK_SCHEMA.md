# Universal Trading Event Schema

Complete documentation for the standardized webhook schema supporting multiple TradingView strategies.

## Overview

The Universal Trading Event Schema provides a consistent, clean format for all trading signals. This enables:

- **Multi-Strategy Support**: Run multiple Pine Script strategies with consistent formatting
- **Strategy-Aware AI Analysis**: AI prompts adapt to each strategy's profile
- **Better Analytics**: Track performance by strategy and event type
- **Clean Structure**: Organized nested objects for price and context data
- **Type Safety**: Proper validation and type checking

---

## Schema Format

### Complete Example

```json
{
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
    "sweptLevel": 91950.00,
    "htfBias": "bullish",
    "displacement": true
  },
  "timestamp": "2026-01-14T10:30:00Z"
}
```

### Minimal Example

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Simple Reversal",
  "eventType": "reversal",
  "direction": "bullish",
  "session": "London",
  "price": {
    "close": 92100.50
  }
}
```

---

## Field Reference

### Top-Level Required Fields

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `symbol` | string | Required | Trading pair (e.g., "BTCUSDT") |
| `timeframe` | string | Required | Chart timeframe (e.g., "5", "15", "1h") |
| `strategy` | string | Required | Strategy name (e.g., "Liquidity Sweeps") |
| `eventType` | string | Required | Must be: `reversal`, `continuation`, `breakout`, or `sweep` |
| `direction` | string | Required | Must be: `bullish` or `bearish` |
| `session` | string | Required | Trading session: `London`, `NY`, or `Asia` |
| `price` | object | Required | Price information (see below) |
| `context` | object | Optional | Context information (see below) |
| `timestamp` | string (ISO-8601) | Optional | Event timestamp (auto-generated if not provided) |

### Price Object (Required)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `close` | number | Yes | Current close price |
| `stopLoss` | number | No | Suggested stop loss from strategy |

### Context Object (Optional)

| Field | Type | Description |
|-------|------|-------------|
| `sweptLevel` | number | Price level that was swept (for sweep events) |
| `htfBias` | string | Higher timeframe bias: `"bullish"` or `"bearish"` |
| `displacement` | boolean | Strong price movement detected |
| `volumeSpike` | boolean | Volume spike detected |
| `previousDayHigh` | number | Previous day high for reference |
| `previousDayLow` | number | Previous day low for reference |

---

## Event Types

### 1. Reversal (`reversal`)

**Use for**: Trend reversals, two-candle patterns, RSI extremes

**Characteristics**:
- Counter-trend entry
- Entry after confirmation
- Stop beyond reversal point
- Typical R:R: 1:2-1:4

**Example**:
```json
{
  "eventType": "reversal",
  "direction": "bullish",
  "price": {
    "close": 92100.50,
    "stopLoss": 91800.00
  },
  "context": {
    "displacement": true,
    "volumeSpike": true
  }
}
```

### 2. Continuation (`continuation`)

**Use for**: Pullbacks, trend continuation, retest entries

**Characteristics**:
- With-trend entry
- Entry on pullback to key level
- Stop beyond structure
- Typical R:R: 1:2-1:3

**Example**:
```json
{
  "eventType": "continuation",
  "direction": "bullish",
  "price": {
    "close": 93500.00,
    "stopLoss": 93000.00
  },
  "context": {
    "htfBias": "bullish"
  }
}
```

### 3. Breakout (`breakout`)

**Use for**: Range breaks, consolidation breakouts

**Characteristics**:
- Entry on break confirmation
- Stop inside range
- Wide targets
- Typical R:R: 1:5-1:10+

**Example**:
```json
{
  "eventType": "breakout",
  "direction": "bullish",
  "price": {
    "close": 105.50,
    "stopLoss": 103.00
  },
  "context": {
    "volumeSpike": true,
    "displacement": true
  }
}
```

### 4. Sweep (`sweep`)

**Use for**: Liquidity sweeps, stop hunts, false breakouts

**Characteristics**:
- Entry beyond swept level
- Stop past sweep invalidation
- Clear swept level required
- Typical R:R: 1:3-1:5

**Example**:
```json
{
  "eventType": "sweep",
  "direction": "bullish",
  "price": {
    "close": 92100.50,
    "stopLoss": 91800.00
  },
  "context": {
    "sweptLevel": 91950.00,
    "displacement": true
  }
}
```

---

## Supported Strategies

### 1. Liquidity Sweeps

**Profile**: 45-55% win rate, 1:3-1:5 risk:reward

**Event Types**: `sweep`, `reversal`

**Key Characteristics**:
- Equal highs/lows swept
- Displacement reversal
- Clear invalidation level

### 2. Candle 2 Closure

**Profile**: 60-70% win rate, 1:2-1:3 risk:reward

**Event Types**: `reversal`

**Key Characteristics**:
- Two consecutive candles closing against trend
- RSI extreme (>70 or <30)
- Volume confirmation

### 3. Custom Strategies

You can add your own strategies:

**Guidelines**:
1. Choose a unique strategy name
2. Use standard event types
3. Provide contextual data
4. System will adapt AI analysis automatically

---

## Pine Script Examples

### Candle 2 Closure Strategy

```javascript
// In your Pine Script strategy
if (reversal_signal) {
    strategy.entry("Long", strategy.long)

    alert('{' +
      '"symbol": "' + syminfo.ticker + '",' +
      '"timeframe": "' + timeframe.period + '",' +
      '"strategy": "Candle 2 Closure",' +
      '"eventType": "reversal",' +
      '"direction": "bullish",' +
      '"session": "London",' +
      '"price": {' +
        '"close": ' + str.tostring(close) + ',' +
        '"stopLoss": ' + str.tostring(low[1]) +
      '},' +
      '"context": {' +
        '"htfBias": "bullish",' +
        '"displacement": true,' +
        '"volumeSpike": ' + str.tostring(vol_spike) +
      '}' +
    '}', alert.freq_once_per_bar)
}
```

### Liquidity Sweeps Strategy

```javascript
// In your Pine Script strategy
if (sweep_detected) {
    strategy.entry("Long", strategy.long)

    alert('{' +
      '"symbol": "' + syminfo.ticker + '",' +
      '"timeframe": "' + timeframe.period + '",' +
      '"strategy": "Liquidity Sweeps",' +
      '"eventType": "sweep",' +
      '"direction": "bullish",' +
      '"session": "NY",' +
      '"price": {' +
        '"close": ' + str.tostring(close) + ',' +
        '"stopLoss": ' + str.tostring(stop_price) +
      '},' +
      '"context": {' +
        '"sweptLevel": ' + str.tostring(swept_high) + ',' +
        '"htfBias": "bullish",' +
        '"displacement": true,' +
        '"volumeSpike": true' +
      '}' +
    '}', alert.freq_once_per_bar)
}
```

### Simple Strategy (Minimal)

```javascript
// Minimal required fields
if (signal) {
    alert('{' +
      '"symbol": "' + syminfo.ticker + '",' +
      '"timeframe": "' + timeframe.period + '",' +
      '"strategy": "My Strategy",' +
      '"eventType": "reversal",' +
      '"direction": "bullish",' +
      '"session": "London",' +
      '"price": {' +
        '"close": ' + str.tostring(close) +
      '}' +
    '}', alert.freq_once_per_bar)
}
```

---

## Response Format

### Synchronous Response (HTTP 202 Accepted)

```json
{
  "success": true,
  "status": "ACCEPTED",
  "message": "Webhook received and processing started",
  "symbol": "BTCUSDT",
  "strategy": "Candle 2 Closure",
  "eventType": "reversal",
  "direction": "bullish"
}
```

### Asynchronous Processing

The webhook is processed asynchronously:

1. **Signal Analysis**: AI analyzes market context
2. **Rule Validation**: Trading rules enforced
3. **Journal Entry**: Signal logged to database
4. **Telegram Notification**: Sent if signal is VALID

---

## Testing

### cURL Test

```bash
curl -X POST http://localhost:8080/api/webhook/test \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key" \
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
  }'
```

### Test Script

```bash
cd test-payloads
./test-webhook.sh
```

---

## AI Analysis Adaptation

The AI analysis automatically adapts based on strategy and event type:

### Strategy-Specific Analysis

Each strategy gets tailored AI prompts:

```
Strategy: Liquidity Sweeps
→ AI focuses on: swept levels, displacement, reversal confirmation

Strategy: Candle 2 Closure
→ AI focuses on: candle pattern quality, volume, RSI extremes

Strategy: Custom
→ AI focuses on: generic event type criteria
```

### Event Type Analysis

Event type determines what AI looks for:

- **Reversal**: Exhaustion signals, divergence, pattern quality
- **Continuation**: Trend strength, pullback quality
- **Breakout**: Consolidation quality, volume surge
- **Sweep**: Swept level significance, reversal displacement

---

## Database Schema

Signals are stored with universal schema fields:

```sql
-- Fields in journal_entries table
strategy VARCHAR(100)          -- Strategy name
event_type VARCHAR(50)         -- Event type
swept_level DECIMAL(20, 8)    -- Swept price level
suggested_stop_loss DECIMAL    -- Strategy-suggested SL
```

### Performance Analytics

Query by strategy and event type:

```sql
SELECT strategy, event_type,
       COUNT(*) as signals,
       AVG(CASE WHEN outcome = 'WIN' THEN 1 ELSE 0 END) as win_rate
FROM journal_entries
WHERE strategy IS NOT NULL
GROUP BY strategy, event_type;
```

---

## Best Practices

1. **Be Consistent**: Use the same strategy name across all alerts
2. **Choose Correct Event Types**: Match event type to actual pattern
3. **Provide Context**: More context = better AI analysis
4. **Test First**: Use `/test` endpoint before production
5. **Monitor Performance**: Track by strategy/event type
6. **Use Stop Loss**: Always include `stopLoss` in price object when available

---

## Validation Rules

The system validates:

- ✅ All required fields present
- ✅ Event type is one of: reversal, continuation, breakout, sweep
- ✅ Direction is either: bullish or bearish
- ✅ Price object has `close` field
- ✅ Numeric fields are valid numbers
- ✅ Timestamp is valid ISO-8601 format (if provided)

---

## Support

- **API Documentation**: [API_ENDPOINTS.md](API_ENDPOINTS.md)
- **Setup Guide**: [SETUP.md](../SETUP.md)
- **Development Guide**: [DEVELOPMENT.md](../DEVELOPMENT.md)

---

**Clean, standardized, and extensible! 🚀**

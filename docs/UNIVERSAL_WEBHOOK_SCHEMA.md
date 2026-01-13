# Universal Trading Event Schema

Complete documentation for the standardized webhook schema supporting multiple TradingView strategies.

## Overview

The Universal Trading Event Schema provides a consistent format for all trading signals across different strategies. This enables:

- **Multi-Strategy Support**: Run multiple Pine Script strategies with consistent formatting
- **Strategy-Aware AI Analysis**: AI prompts adapt to each strategy's profile
- **Better Analytics**: Track performance by strategy and event type
- **Future-Proof**: Easy to add new strategies without code changes
- **Backward Compatible**: Legacy format still supported

---

## Quick Start

### Universal Schema Format

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Liquidity Sweeps",
  "event_type": "sweep",
  "direction": "bullish",
  "session": "London",
  "price": {
    "close": 92100.50,
    "stop_loss": 91800.00
  },
  "context": {
    "swept_level": 91950.00,
    "htf_bias": "bullish",
    "displacement": true,
    "volume_spike": true,
    "previous_day_high": 92500.00,
    "previous_day_low": 91500.00
  },
  "timestamp": "2026-01-14T10:30:00Z"
}
```

### Alternative Flat Format

Both nested and flat formats are supported. Use whichever is easier for your Pine Script:

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "15",
  "strategy": "Liquidity Sweeps",
  "event_type": "sweep",
  "direction": "bullish",
  "session": "London",
  "currentPrice": 92100.50,
  "suggestedStopLoss": 91800.00,
  "sweptLevel": 91950.00,
  "htfBias": "bullish",
  "displacement": true,
  "volumeSpike": true,
  "previousDayHigh": 92500.00,
  "previousDayLow": 91500.00,
  "timestamp": "2026-01-14T10:30:00Z"
}
```

---

## Field Reference

### Required Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `symbol` | string | Trading pair | `"BTCUSDT"` |
| `timeframe` | string | Chart timeframe | `"5"`, `"15"`, `"1h"` |
| `strategy` | string | Strategy name | `"Liquidity Sweeps"` |
| `event_type` | string | Event type | `"reversal"`, `"continuation"`, `"breakout"`, `"sweep"` |
| `direction` | string | Trade direction | `"bullish"` or `"bearish"` |
| `session` | string | Trading session | `"London"`, `"NY"`, `"Asia"` |

### Price Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `price.close` or `currentPrice` | number | Yes | Current close price |
| `price.stop_loss` or `suggestedStopLoss` | number | No | Suggested stop loss from strategy |

### Context Fields

All context fields are optional but recommended for better AI analysis:

| Field | Type | Description |
|-------|------|-------------|
| `context.swept_level` or `sweptLevel` | number | Price level that was swept (for liquidity events) |
| `context.htf_bias` or `htfBias` | string | Higher timeframe bias: `"bullish"` or `"bearish"` |
| `context.displacement` or `displacement` | boolean | Strong price movement detected |
| `context.volume_spike` or `volumeSpike` | boolean | Volume spike detected |
| `context.previous_day_high` | number | Previous day high |
| `context.previous_day_low` | number | Previous day low |

### Optional Fields

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | string (ISO-8601) | Event timestamp (auto-generated if not provided) |

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
  "event_type": "reversal",
  "direction": "bullish",
  "context": {
    "displacement": true,
    "volume_spike": true
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
  "event_type": "continuation",
  "direction": "bullish",
  "context": {
    "htf_bias": "bullish"
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
  "event_type": "breakout",
  "direction": "bullish",
  "context": {
    "volume_spike": true,
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
  "event_type": "sweep",
  "direction": "bullish",
  "context": {
    "swept_level": 91950.00,
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

**Pine Script Example**:
```javascript
strategy.entry("Long", strategy.long)
alert('{' +
  '"symbol": "' + syminfo.ticker + '",' +
  '"timeframe": "' + timeframe.period + '",' +
  '"strategy": "Liquidity Sweeps",' +
  '"event_type": "sweep",' +
  '"direction": "bullish",' +
  '"session": "' + session + '",' +
  '"currentPrice": ' + str.tostring(close) + ',' +
  '"sweptLevel": ' + str.tostring(swept_high) + ',' +
  '"displacement": true' +
'}', alert.freq_once_per_bar)
```

### 2. Candle 2 Closure RSI

**Profile**: 60-70% win rate, 1:2-1:3 risk:reward

**Event Types**: `reversal`

**Key Characteristics**:
- RSI extreme (>70 or <30)
- Two consecutive candles closing against trend
- Volume confirmation

**Pine Script Example**:
```javascript
strategy.entry("Long", strategy.long)
alert('{' +
  '"symbol": "' + syminfo.ticker + '",' +
  '"timeframe": "' + timeframe.period + '",' +
  '"strategy": "Candle 2 Closure RSI",' +
  '"event_type": "reversal",' +
  '"direction": "bullish",' +
  '"session": "' + session + '",' +
  '"currentPrice": ' + str.tostring(close) + ',' +
  '"suggestedStopLoss": ' + str.tostring(stop_loss) + ',' +
  '"volumeSpike": ' + str.tostring(vol_spike) +
'}', alert.freq_once_per_bar)
```

### 3. Custom Strategies

You can add your own strategies without code changes:

**Guidelines**:
1. Choose a unique strategy name
2. Use standard event types
3. Provide contextual data
4. System will adapt AI analysis automatically

**Example - Breakout Strategy**:
```json
{
  "symbol": "ETHUSDT",
  "timeframe": "30",
  "strategy": "Range Breakout Pro",
  "event_type": "breakout",
  "direction": "bullish",
  "session": "NY",
  "currentPrice": 2100.50,
  "suggestedStopLoss": 2090.00,
  "volumeSpike": true,
  "displacement": true
}
```

---

## AI Analysis Adaptation

The AI analysis automatically adapts based on strategy and event type:

### Strategy-Specific Analysis

Each strategy gets tailored AI prompts:

```
Strategy: Liquidity Sweeps
→ AI focuses on: swept levels, displacement, reversal confirmation

Strategy: Candle 2 Closure RSI
→ AI focuses on: RSI extremes, candle pattern quality, volume

Strategy: Custom Breakout
→ AI focuses on: consolidation quality, volume surge, momentum
```

### Event Type Analysis

Event type determines what AI looks for:

- **Reversal**: Exhaustion signals, divergence, reversal pattern quality
- **Continuation**: Trend strength, pullback quality, resumption confirmation
- **Breakout**: Consolidation tightness, volume surge, momentum strength
- **Sweep**: Swept level significance, reversal displacement, invalidation clarity

---

## Response Format

### Synchronous Response (HTTP 202 Accepted)

```json
{
  "success": true,
  "status": "ACCEPTED",
  "message": "Webhook received and processing started",
  "symbol": "BTCUSDT",
  "strategy": "Liquidity Sweeps",
  "event_type": "sweep",
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

### cURL Test - Universal Schema

```bash
curl -X POST http://localhost:8080/api/webhook/tradingview \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key" \
  -d '{
    "symbol": "BTCUSDT",
    "timeframe": "15",
    "strategy": "Liquidity Sweeps",
    "event_type": "sweep",
    "direction": "bullish",
    "session": "London",
    "currentPrice": 92100.50,
    "sweptLevel": 91950.00,
    "displacement": true,
    "volumeSpike": true,
    "htfBias": "bullish"
  }'
```

### Test Endpoint

Use `/api/webhook/test` for synchronous testing:

```bash
curl -X POST http://localhost:8080/api/webhook/test \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key" \
  -d '{
    "symbol": "BTCUSDT",
    "strategy": "Liquidity Sweeps",
    "event_type": "sweep",
    "direction": "bullish",
    ...
  }'
```

Returns full `TradeSignal` object with AI analysis.

---

## Migration from Legacy Format

### Legacy Format

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "liquidity_sweep_long",
  "session": "NY",
  "price": 43120.50,
  "sweptHigh": 43150.00,
  "displacementDetected": true,
  "volumeSpike": true,
  "htfBias": "bullish"
}
```

### Automatic Conversion

The system automatically converts legacy format:

| Legacy Field | Universal Field |
|--------------|-----------------|
| `event` | Parsed to `strategy`, `event_type`, `direction` |
| `price` | `currentPrice` |
| `sweptHigh` / `sweptLow` | `sweptLevel` |
| `displacementDetected` | `displacement` |

### Migration Steps

1. **Phase 1**: Both formats work (current)
2. **Phase 2**: Update Pine Scripts to universal format
3. **Phase 3**: Legacy format may be deprecated (with advance notice)

---

## Database Schema

Signals are stored with universal schema fields:

```sql
-- New fields in journal_entries table
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

---

## Support

- **API Documentation**: [API_ENDPOINTS.md](API_ENDPOINTS.md)
- **Setup Guide**: [SETUP.md](../SETUP.md)
- **Development Guide**: [DEVELOPMENT.md](../DEVELOPMENT.md)

---

**Ready to standardize your strategies! 🚀**

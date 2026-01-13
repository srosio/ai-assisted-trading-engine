# Trading Strategies Guide

## Overview

This document describes the 5 Pine Script v6 trading strategies fully integrated with the AI-Assisted Trading Engine backend. Each strategy sends properly formatted webhook payloads that the AI analyzes for trade quality and execution planning.

---

## Strategy Summary

| Strategy | Win Rate | Risk:Reward | Best For | Event Keywords |
|----------|----------|-------------|----------|----------------|
| **High Win Rate Scalping** | 70-80% | 1:1 - 1:2 | Mean reversion at extremes | `scalping`, `bb_extreme` |
| **Medium Win Rate Swing** | 50-60% | 1:2 - 1:4 | Trend continuation pullbacks | `swing`, `pullback`, `fib_retracement` |
| **Low Win Rate Breakout** | 30-40% | 1:5 - 1:10+ | Explosive range breakouts | `breakout`, `range_break` |
| **Liquidity Sweep** | 45-55% | 1:3 - 1:5 | False breakout reversals | `liquidity`, `sweep`, `reversal` |
| **Adaptive Strategy** | 40-60% | 1:2 - 1:4 | Multi-regime switching | `adaptive`, `regime`, `multi_strategy` |

---

## 1. High Win Rate Scalping Strategy

### Description
Mean reversion strategy that trades bounces from Bollinger Band extremes with RSI confirmation. Designed for quick in-and-out trades with tight stops.

### Key Indicators
- **Bollinger Bands (20, 2.0)** - Identifies price extremes
- **RSI (14)** - Confirms oversold (<30) / overbought (>70)
- **Volume Spike** - 2x average confirms conviction

### Entry Signals
- **Long**: Close ≤ BB Lower + RSI ≤ 30 + Close > Low (wick rejection)
- **Short**: Close ≥ BB Upper + RSI ≥ 70 + Close < High (wick rejection)

### Webhook Events
- `scalping_long_bb_extreme`
- `scalping_short_bb_extreme`

### Best Timeframes
- 1m, 3m, 5m (scalping)

### Expected Performance
- Win Rate: 70-80%
- Risk:Reward: 1:1 to 1:2
- Typical Stop: 0.3-0.5% from entry

---

## 2. Medium Win Rate Swing Strategy

### Description
Trend continuation strategy that enters on Fibonacci retracement pullbacks in established trends. Waits for price to return to the golden zone before continuation.

### Key Indicators
- **EMA (20, 50, 200)** - Trend identification
- **Fibonacci Retracement (0.618)** - Optimal entry zone
- **Volume Spike** - 1.5x confirms reversal completion

### Entry Signals
- **Long**: Uptrend + Low touches Fib 0.618 + Close > Fib 0.618 + Volume spike
- **Short**: Downtrend + High touches Fib 0.618 + Close < Fib 0.618 + Volume spike

### Webhook Events
- `swing_long_fib_pullback`
- `swing_short_fib_pullback`

### Best Timeframes
- 15m, 1H, 4H (swing trading)

### Expected Performance
- Win Rate: 50-60%
- Risk:Reward: 1:2 to 1:4
- Typical Stop: 1-2% from entry

---

## 3. Low Win Rate Breakout Strategy

### Description
Explosive breakout strategy that catches powerful moves from tight consolidations. Low win rate but massive reward potential when it works.

### Key Indicators
- **Range Detection (30 bars)** - Identifies consolidation
- **ATR (14)** - Measures volatility
- **Volume Surge** - 3x confirms breakout validity
- **RSI Momentum** - Confirms directional strength

### Entry Signals
- **Long**: Consolidation + Close > Range High + Volume surge + Strong momentum
- **Short**: Consolidation + Close < Range Low + Volume surge + Strong momentum

### Webhook Events
- `breakout_long_range_break`
- `breakout_short_range_break`

### Best Timeframes
- 5m, 15m, 1H (breakout hunting)

### Expected Performance
- Win Rate: 30-40%
- Risk:Reward: 1:5 to 1:10+
- Typical Stop: Inside the consolidation range

---

## 4. Liquidity Sweep Strategy

### Description
Identifies false breakouts (liquidity sweeps) where price briefly breaks pivot levels to trigger stops, then reverses. Trades the reversal with displacement confirmation.

### Key Indicators
- **Pivot Highs/Lows (10 bars)** - Liquidity levels
- **ATR Displacement** - 1.5x ATR confirms reversal
- **Volume Spike** - 2x confirms institutional interest

### Entry Signals
- **Long**: Price sweeps below pivot low + Reverses above pivot + Displacement up + Volume
- **Short**: Price sweeps above pivot high + Reverses below pivot + Displacement down + Volume

### Webhook Events
- `liquidity_long_sweep_reversal`
- `liquidity_short_sweep_reversal`

### Best Timeframes
- 3m, 5m, 15m (liquidity hunting)

### Expected Performance
- Win Rate: 45-55%
- Risk:Reward: 1:3 to 1:5
- Typical Stop: Beyond the swept level

---

## 5. Adaptive Multi-Regime Strategy

### Description
Intelligent strategy that identifies market regime (trending, ranging, volatile) and adapts entry logic accordingly. Switches between pullback, mean reversion, and breakout modes.

### Key Indicators
- **ADX (14)** - Trend strength measurement
- **Bollinger Bands (20, 2.0)** - Volatility measurement
- **EMA (20, 50, 200)** - Trend structure
- **DMI (DI+, DI-)** - Directional momentum

### Regime Detection
1. **UPTREND**: ADX > 25 + EMA 20 > EMA 50 > EMA 200
2. **DOWNTREND**: ADX > 25 + EMA 20 < EMA 50 < EMA 200
3. **RANGING**: ADX < 25 + Narrow BBs
4. **VOLATILE**: Wide BBs + High ATR

### Entry Signals
- **Trending**: Pullback to EMA 20
- **Ranging**: Mean reversion at BB extremes
- **Volatile**: Momentum breakout with DMI confirmation

### Webhook Events
- `adaptive_long_trend_pullback`
- `adaptive_short_trend_pullback`
- `adaptive_long_range_reversal`
- `adaptive_short_range_reversal`
- `adaptive_long_volatile_breakout`
- `adaptive_short_volatile_breakout`

### Best Timeframes
- 15m, 1H, 4H (all market conditions)

### Expected Performance
- Win Rate: 40-60% (regime-dependent)
- Risk:Reward: 1:2 to 1:4
- Typical Stop: Adaptive based on regime

---

## Webhook Payload Structure

All strategies send consistent JSON payloads to your webhook endpoint:

```json
{
  "symbol": "BTCUSDT",
  "timeframe": "5m",
  "event": "scalping_long_bb_extreme",
  "session": "LONDON_OPEN",
  "price": 45250.50,
  "previousDayHigh": 45800.00,
  "previousDayLow": 44500.00,
  "volumeSpike": true,
  "displacementDetected": false,
  "sweptHigh": null,
  "sweptLow": 45100.25,
  "htfBias": "BULLISH"
}
```

### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `symbol` | string | Trading pair (e.g., BTCUSDT) |
| `timeframe` | string | Chart timeframe (1m, 5m, 15m, 1H, 4H, D) |
| `event` | string | Strategy identifier (recognized by AI) |
| `session` | string | Trading session (LONDON_OPEN, NY_OPEN, ASIA_OPEN) |
| `price` | number | Current close price |
| `previousDayHigh` | number | Yesterday's high for context |
| `previousDayLow` | number | Yesterday's low for context |
| `volumeSpike` | boolean | Volume > threshold multiplier |
| `displacementDetected` | boolean | Strong candle move detected |
| `sweptHigh` | number/null | Price level swept above (liquidity grab) |
| `sweptLow` | number/null | Price level swept below (liquidity grab) |
| `htfBias` | string | Higher timeframe bias (BULLISH, BEARISH, NEUTRAL) |

---

## Setup Instructions

### Step 1: Add Strategy to TradingView

1. Open TradingView and select your chart
2. Click **Pine Editor** at the bottom
3. Copy and paste one of the strategy codes:
   - `SCALPING_STRATEGY.pine`
   - `SWING_STRATEGY.pine`
   - `BREAKOUT_STRATEGY.pine`
   - `LIQUIDITY_SWEEP_STRATEGY.pine`
   - `ADAPTIVE_STRATEGY.pine`
4. Click **"Add to Chart"**

### Step 2: Configure Webhook URL

In the strategy code, update the webhook URL input:

```javascript
webhookUrl = input.string("https://ai-trading-engine.onrender.com/api/webhook/tradingview?apiKey=YOUR_KEY", "Webhook URL")
```

Replace:
- `ai-trading-engine.onrender.com` with your deployed app URL
- `YOUR_KEY` with your actual webhook API key

### Step 3: Create Alert

1. Click **"Create Alert"** (alarm icon)
2. **Condition**: Select your strategy indicator
3. **Alert name**: e.g., "BTC Scalping Strategy"
4. **Message**: Leave blank (uses alert() function in code)
5. **Webhook URL**: Enter your webhook URL
6. **Options**:
   - ✅ Webhook
   - ✅ Once Per Bar Close
7. Click **"Create"**

### Step 4: Verify Integration

Test the webhook:

```bash
# Check health endpoint
curl https://ai-trading-engine.onrender.com/api/webhook/health

# Expected response:
{"status":"UP","service":"AI-Assisted Trading Engine"}
```

---

## AI Integration

### How the AI Analyzes Your Signals

When a webhook is received, the backend:

1. **Validates payload** - Ensures all required fields present
2. **Deduplicates** - Prevents processing the same signal twice
3. **Fetches market data** - Gets OI, funding rate, volume from Binance
4. **Builds intraday context** - Analyzes 5m/15m trends and session narrative
5. **AI analysis** - Claude assesses setup quality (A/B/C grade)
6. **Rule validation** - Checks session, volatility, quality filters
7. **Execution plan** - AI generates entry, stop, targets with R:R
8. **Journal entry** - Stores signal for performance tracking
9. **Telegram notification** - Sends formatted signal with full analysis

### Strategy Recognition

The AI recognizes strategy types from the `event` field:

- **Contains "scalping"** → High Win Rate Scalping (70-80% WR, 1:1-1:2 R:R)
- **Contains "swing"** → Medium Win Rate Swing (50-60% WR, 1:2-1:4 R:R)
- **Contains "breakout"** → Low Win Rate Breakout (30-40% WR, 1:5-1:10+ R:R)
- **Contains "adaptive"** → Adaptive Strategy (40-60% WR, 1:2-1:4 R:R)
- **Contains "liquidity"** → Liquidity Sweep (45-55% WR, 1:3-1:5 R:R)

The AI then:
- Adjusts quality assessment based on expected win rate
- Validates R:R ratios match strategy profile
- Identifies risks specific to the strategy type

---

## Performance Tracking

### Journal API

Query your trade journal:

```bash
# Get today's signals
GET /api/journal/today

# Get statistics by strategy
GET /api/journal/statistics/by-strategy

# Get all scalping signals
GET /api/journal?event=scalping_long_bb_extreme

# Update trade outcome
PUT /api/journal/{signalId}/outcome
{
  "tradeTaken": true,
  "exitPrice": 45500.00,
  "outcome": "WIN",
  "pnl": 125.50,
  "notes": "Took profit at target 1"
}
```

### Telegram Notifications

You'll receive formatted notifications:

```
✅ INTRADAY TRADE SIGNAL

Symbol: BTCUSDT
Direction: LONG
Event: scalping_long_bb_extreme
Session: LONDON_OPEN
Price: 45250.50

🎯 SETUP ASSESSMENT
Quality: A
Alignment: 85/100
Summary: Strong mean reversion setup at BB lower...

📊 EXECUTION PLAN
Entry: 45250-45270 (limit order zone)
Stop: 45100 (1.5 ATR below entry)
Target 1: 45400 (1:1 R:R)
Target 2: 45550 (1:2 R:R)

⚠️ RISK FACTORS
• High funding rate may indicate crowded positioning
• Session transition volatility

Signal ID: abc123-def456
```

---

## Best Practices

### 1. Strategy Selection

- **Scalping**: Use during high liquidity (London/NY overlap)
- **Swing**: Use on higher timeframes (1H, 4H) for quality setups
- **Breakout**: Avoid during low volume (Asia session)
- **Liquidity Sweep**: Best during session opens (liquidity events)
- **Adaptive**: Works in all conditions but requires HTF alignment

### 2. Timeframe Recommendations

| Strategy | Primary TF | Secondary TF | HTF Context |
|----------|------------|--------------|-------------|
| Scalping | 1m, 3m, 5m | 15m | 1H |
| Swing | 15m, 1H | 4H | Daily |
| Breakout | 5m, 15m | 1H | 4H |
| Liquidity Sweep | 3m, 5m, 15m | 1H | 1H |
| Adaptive | 15m, 1H, 4H | Daily | Daily |

### 3. Risk Management

- **Never risk more than 1% per trade** (configured in backend)
- **Respect quality grades**: Only take A/B setups
- **Check HTF bias**: Trade with higher timeframe direction
- **Monitor funding rates**: Avoid extremely crowded positions
- **Session awareness**: Each strategy performs better in specific sessions

### 4. Backtesting

Before going live:
1. Add strategy to TradingView chart
2. Enable **Strategy Tester** (not just indicator)
3. Review performance metrics (win rate, profit factor, drawdown)
4. Compare actual results vs. expected strategy profile
5. Adjust parameters if needed

---

## Troubleshooting

### Webhook Not Triggering

1. Check alert is created and enabled
2. Verify webhook URL is correct
3. Test with `/test` endpoint:
   ```bash
   POST /api/webhook/test
   {
     "symbol": "BTCUSDT",
     "timeframe": "5m",
     "event": "scalping_long_bb_extreme",
     ...
   }
   ```

### Invalid Payload Errors

- Ensure all required fields are present
- Check JSON format (no trailing commas)
- Verify `null` values use lowercase (not `NULL` or `None`)
- Boolean values must be `true`/`false` (not `"true"` strings)

### AI Analysis Fails

- System automatically falls back to static quantitative analysis
- Check Telegram for error notifications
- Verify `CLAUDE_API_KEY` is set (optional but recommended)

### No Telegram Notifications

- Verify `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set
- Check bot has permission to send messages
- Review logs for delivery errors

---

## Strategy Comparison Matrix

| Aspect | Scalping | Swing | Breakout | Liquidity Sweep | Adaptive |
|--------|----------|-------|----------|-----------------|----------|
| **Win Rate** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Risk:Reward** | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Trade Frequency** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Trend Markets** | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| **Range Markets** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Beginner Friendly** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **Requires Monitoring** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

---

## License & Disclaimer

These strategies are for educational purposes only. Past performance does not guarantee future results. Always trade with risk capital you can afford to lose.

**Risk Warning**: Cryptocurrency trading carries significant risk. The strategies provided are examples and should be thoroughly tested and customized to your risk tolerance before live trading.

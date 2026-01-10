# Pine Script Trading Strategies Guide

## Overview

This guide covers 5 different Pine Script strategies with varying win rates and risk/reward profiles. Each strategy is designed for different market conditions, trading styles, and psychological profiles.

---

## Strategy Comparison Table

| Strategy | Win Rate | Avg R:R | Trades/Day | Best Timeframe | Market Type | Difficulty | Screen Time |
|----------|----------|---------|------------|----------------|-------------|------------|-------------|
| **High Win Rate Scalping** | 70-80% | 1:1 to 1:2 | 5-15 | 1m, 5m, 15m | Ranging | Medium | High |
| **Medium Win Rate Swing** | 50-60% | 1:2 to 1:4 | 1-3 | 15m, 1h, 4h | Trending | Easy | Low |
| **Low Win Rate Breakout** | 30-40% | 1:5 to 1:10+ | 0-1 | 1h, 4h, Daily | Breakout | Hard | Very Low |
| **Adaptive Strategy** | 40-60% | 1:2 to 1:4 | 2-5 | 15m, 1h | All | Medium | Medium |
| **Liquidity Sweep** | 45-55% | 1:3 to 1:5 | 1-4 | 5m, 15m | Trending | Medium | Medium |

---

## 1. High Win Rate Scalping Strategy

**File:** `PINE_SCRIPT_HIGH_WINRATE_SCALPING.pine`

### Characteristics
- **Win Rate:** 70-80%
- **Average R:R:** 1:1 to 1:2
- **Frequency:** Very high (5-15 trades per day)
- **Trade Duration:** Minutes to hours
- **Best Markets:** Ranging, choppy

### Strategy Logic
- Mean reversion at Bollinger Band extremes
- RSI overbought/oversold confirmation
- Tight stops near entry
- Quick profit targets (BB middle or opposite band)
- High frequency to capitalize on small moves

### Pros
✅ Psychological benefit of frequent wins
✅ Small per-trade risk
✅ Works well in ranging markets
✅ Many opportunities throughout the day

### Cons
❌ Requires constant monitoring
❌ High commission costs
❌ Small profit per trade
❌ Vulnerable to sudden trends
❌ Screen fatigue

### Best For
- Full-time traders with time to monitor
- Traders who prefer frequent feedback
- Those comfortable with small gains
- Markets with clear support/resistance

### Risk Management
- **Stop Loss:** Just beyond Bollinger Band (~0.3-0.5% risk)
- **Take Profit:** Middle BB or opposite BB (1-2R)
- **Position Size:** Standard 0.5-1% risk per trade
- **Daily Limit:** Stop after 3 consecutive losses

### Sample Performance
```
100 trades:
- 75 wins at 1R = +75R
- 25 losses at -1R = -25R
- Net: +50R profit
- Commissions: -5R (0.05R per trade)
- Final: +45R (45% account growth at 1% risk)
```

---

## 2. Medium Win Rate Swing Strategy

**File:** `PINE_SCRIPT_MEDIUM_WINRATE_SWING.pine`

### Characteristics
- **Win Rate:** 50-60%
- **Average R:R:** 1:2 to 1:4
- **Frequency:** Moderate (1-3 trades per day)
- **Trade Duration:** Hours to days
- **Best Markets:** Trending with clear structure

### Strategy Logic
- Trend following with pullbacks
- Enter when price retraces to Fibonacci levels or moving averages
- Ride the trend continuation
- Exit at opposite swing levels

### Pros
✅ Balanced approach
✅ Less stressful than scalping
✅ Good for part-time traders
✅ Strong edge in trending markets

### Cons
❌ Requires patience for pullbacks
❌ Losses can be moderate
❌ Needs clear trend identification

### Best For
- Part-time traders
- Those who prefer fewer, higher-quality trades
- Traders comfortable with 50/50 outcomes
- Trending market environments

### Risk Management
- **Stop Loss:** Beyond last swing low/high (~1-2% risk)
- **Take Profit:** Previous swing high/low (2-4R)
- **Trail Stop:** After 1:1 achieved
- **Position Size:** Standard 1% risk per trade

### Sample Performance
```
100 trades:
- 55 wins at 3R = +165R
- 45 losses at -1R = -45R
- Net: +120R profit
- Final: +120R (120% account growth at 1% risk)
```

---

## 3. Low Win Rate Breakout Strategy

**File:** `PINE_SCRIPT_LOW_WINRATE_BREAKOUT.pine`

### Characteristics
- **Win Rate:** 30-40%
- **Average R:R:** 1:5 to 1:10+
- **Frequency:** Very low (0-1 trades per day)
- **Trade Duration:** Days to weeks
- **Best Markets:** Post-consolidation explosions

### Strategy Logic
- Identify consolidation ranges
- Wait for volume surge and breakout
- Enter on confirmed momentum
- Let winners run with trailing stops

### Pros
✅ Massive winners when correct
✅ One winner covers many losses
✅ Low screen time
✅ Catches explosive moves

### Cons
❌ Psychologically challenging
❌ Many consecutive losses normal
❌ Long wait between signals
❌ Requires strong discipline
❌ Not for beginners

### Best For
- Experienced traders with strong psychology
- Those who can handle losing streaks
- Traders seeking home runs
- Large accounts (to handle drawdowns)

### Risk Management
- **Stop Loss:** Inside range (~2-3% risk)
- **Take Profit:** Multiple targets (5R, 10R, 20R+)
- **Trail Stop:** Aggressive after 3R achieved
- **Position Size:** REDUCED to 0.5% risk (critical!)

### Sample Performance
```
100 trades:
- 35 wins: 10 at 5R, 15 at 8R, 10 at 12R = +290R
- 65 losses at -1R = -65R
- Net: +225R profit
- Final: +225R (112.5% account growth at 0.5% risk)

BUT: Expect 10-15 consecutive losses!
```

### ⚠️ CRITICAL WARNING
This strategy has the highest risk of ruin. You MUST:
- Have sufficient capital (50+ R minimum)
- Use strict position sizing (0.5% max)
- Accept long losing streaks as normal
- Keep detailed statistics
- Never increase size during losses

---

## 4. Adaptive Strategy

**File:** `PINE_SCRIPT_ADAPTIVE_STRATEGY.pine`

### Characteristics
- **Win Rate:** 40-60% (varies by regime)
- **Average R:R:** 1:2 to 1:4
- **Frequency:** Moderate (2-5 trades per day)
- **Trade Duration:** Hours to days
- **Best Markets:** All conditions

### Strategy Logic
- Identifies market regime (trending vs ranging)
- Uses trend following in trending markets
- Uses mean reversion in ranging markets
- Adjusts targets and stops per regime

### Pros
✅ Works in all market conditions
✅ Consistent long-term performance
✅ Balanced risk/reward
✅ Less vulnerable to regime changes

### Cons
❌ More complex to understand
❌ Not optimized for any single condition
❌ Requires regime identification

### Best For
- Traders wanting all-weather approach
- Those building long-term track record
- Traders in variable market conditions
- Intermediate to advanced traders

### Risk Management
- **Trending Markets:**
  - Stop: Beyond swing (~1.5% risk)
  - Target: 3-4R
- **Ranging Markets:**
  - Stop: Beyond BB (~0.8% risk)
  - Target: 1.5-2R

### Sample Performance
```
100 trades (60 trending, 40 ranging):

Trending (Trend Following):
- 30 wins at 3.5R = +105R
- 30 losses at -1R = -30R

Ranging (Mean Reversion):
- 25 wins at 1.75R = +43.75R
- 15 losses at -1R = -15R

Net: +103.75R profit
Final: +103.75R (103.75% growth at 1% risk)
```

---

## 5. Liquidity Sweep Strategy (Original)

**File:** `PINE_SCRIPT_EXAMPLE.pine`

### Characteristics
- **Win Rate:** 45-55%
- **Average R:R:** 1:3 to 1:5
- **Frequency:** Low to moderate (1-4 trades per day)
- **Trade Duration:** Hours to days
- **Best Markets:** Trending with liquidity grabs

### Strategy Logic
- Identifies equal highs/lows (liquidity pools)
- Waits for sweep and reversal
- Enters on displacement in opposite direction
- Targets previous structure

### Pros
✅ Clear entry/exit logic
✅ Good risk/reward
✅ Works with institutional flow
✅ Definable invalidation

### Cons
❌ Requires understanding of market structure
❌ False sweeps common
❌ Needs volatility for displacement

### Best For
- Traders familiar with price action
- Those understanding liquidity concepts
- Intermediate traders
- Trend-following mindset

### Risk Management
- **Stop Loss:** Beyond swept level (~1% risk)
- **Take Profit:** Previous swing (3-5R)
- **Position Size:** 1% risk standard

---

## Choosing the Right Strategy

### Based on Your Personality

**Conservative / Risk-Averse:**
→ High Win Rate Scalping (frequent wins reduce stress)

**Balanced / Patient:**
→ Medium Win Rate Swing or Adaptive Strategy

**Aggressive / High Risk Tolerance:**
→ Low Win Rate Breakout (if psychology is strong)

**Structure-Focused:**
→ Liquidity Sweep Strategy

---

### Based on Available Time

**Full-Time Trader:**
→ High Win Rate Scalping or Adaptive

**Part-Time (1-2 hours/day):**
→ Medium Win Rate Swing

**Set-and-Forget:**
→ Low Win Rate Breakout

---

### Based on Market Conditions

**Trending Market:**
→ Medium Win Rate Swing or Liquidity Sweep

**Ranging Market:**
→ High Win Rate Scalping

**Volatile Breakout Phase:**
→ Low Win Rate Breakout

**Uncertain/Mixed:**
→ Adaptive Strategy

---

## Implementation Guide

### Step 1: Choose Your Strategy
Review the table and descriptions above. Pick ONE strategy to start.

### Step 2: Configure TradingView
1. Copy the appropriate `.pine` file
2. Open TradingView Pine Editor
3. Paste the code
4. Save the indicator
5. Apply to your chart

### Step 3: Set Up Webhook
1. Create alert on the indicator
2. Set webhook URL to: `http://your-server:8080/api/webhook/tradingview`
3. Set webhook body to use the JSON from the script
4. Add `X-API-Key` header with your API key

### Step 4: Configure Trading Engine
Update `application.yml` to match your strategy risk profile:

**High Win Rate Scalping:**
```yaml
trading:
  default-risk-percent: 0.5  # Smaller size for frequency
  allow-b-quality: true
  require-htf-alignment: false  # More flexible
```

**Medium Win Rate Swing:**
```yaml
trading:
  default-risk-percent: 1.0
  allow-b-quality: true
  require-htf-alignment: true
```

**Low Win Rate Breakout:**
```yaml
trading:
  default-risk-percent: 0.5  # CRITICAL: Smaller size
  allow-b-quality: false  # Only A quality
  require-htf-alignment: true
```

### Step 5: Paper Trade First
Test EVERY strategy for at least 50 trades on paper before risking real capital.

### Step 6: Track Performance
Keep detailed statistics:
- Win rate by setup type
- Average R achieved
- Max drawdown
- Consecutive losses
- Best/worst sessions

---

## Risk of Ruin Calculator

Use this formula to check if your account can handle the strategy:

```
Required Account Size = Max Consecutive Losses × Risk per Trade × 2

Example for Breakout Strategy:
- Max consecutive losses: 15
- Risk per trade: 0.5%
- Required: 15 × 0.5% × 2 = 15% account buffer

Minimum account: $10,000
Buffer needed: $1,500
Safe to trade: YES
```

---

## Common Mistakes to Avoid

1. **Mixing Strategies** - Pick ONE and stick to it
2. **Over-leveraging** - Always risk 0.5-1% per trade maximum
3. **Revenge Trading** - Stop after daily loss limit
4. **Ignoring Market Regime** - Scalping doesn't work in trends
5. **Not Tracking Stats** - You can't improve what you don't measure
6. **Abandoning After Losses** - All strategies have drawdowns
7. **Switching Too Soon** - Give 100+ trades before evaluating

---

## Performance Expectations

### Realistic Monthly Returns

**High Win Rate Scalping:**
- Good month: +15-25R (+15-25%)
- Bad month: -5R (-5%)

**Medium Win Rate Swing:**
- Good month: +20-30R (+20-30%)
- Bad month: -8R (-8%)

**Low Win Rate Breakout:**
- Good month: +50R (+25% at 0.5% risk)
- Bad month: -15R (-7.5%)
- **Very streaky!**

**Adaptive:**
- Good month: +15-20R (+15-20%)
- Bad month: -6R (-6%)

---

## Backtesting Recommendations

Before live trading:
1. **Historical test:** 500+ trades
2. **Forward test:** 100+ trades on demo
3. **Micro-live test:** 50 trades at minimum size
4. **Full live:** If all above successful

---

## Final Notes

- **No strategy works 100% of the time**
- **All strategies have drawdown periods**
- **Discipline beats strategy selection**
- **Position sizing is MORE important than win rate**
- **Journal everything for continuous improvement**

Choose wisely based on YOUR personality, schedule, and risk tolerance. The "best" strategy is the one you can execute consistently with discipline.

---

**Remember:**
- The trading engine enforces risk rules (no trade limit since latest update)
- Telegram notifications only sent for VALID setups (rules passed)
- All signals journaled regardless of notification
- Focus on process, not individual trades

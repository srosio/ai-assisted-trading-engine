-- Add Universal Trading Event Schema fields to journal_entries table
-- Migration for standardized multi-strategy support

-- Add strategy field
ALTER TABLE journal_entries
ADD COLUMN strategy VARCHAR(100);

-- Add event_type field (reversal, continuation, breakout, sweep)
ALTER TABLE journal_entries
ADD COLUMN event_type VARCHAR(50);

-- Add swept_level field (for liquidity sweep events)
ALTER TABLE journal_entries
ADD COLUMN swept_level DECIMAL(20, 8);

-- Add suggested_stop_loss field (from strategy)
ALTER TABLE journal_entries
ADD COLUMN suggested_stop_loss DECIMAL(20, 8);

-- Create indexes for new fields to support filtering and analytics
CREATE INDEX idx_journal_strategy ON journal_entries(strategy);
CREATE INDEX idx_journal_event_type ON journal_entries(event_type);

-- Add comments for documentation
COMMENT ON COLUMN journal_entries.strategy IS 'Strategy name (e.g., "Liquidity Sweeps", "Candle 2 Closure RSI")';
COMMENT ON COLUMN journal_entries.event_type IS 'Standardized event type: reversal, continuation, breakout, sweep';
COMMENT ON COLUMN journal_entries.swept_level IS 'Price level that was swept (for liquidity events)';
COMMENT ON COLUMN journal_entries.suggested_stop_loss IS 'Stop loss level suggested by the strategy';

-- Update performance stats view to include strategy breakdown
CREATE OR REPLACE VIEW v_performance_stats AS
SELECT
    COUNT(*) as total_signals,
    COUNT(CASE WHEN trade_taken = true THEN 1 END) as trades_taken,
    COUNT(CASE WHEN outcome = 'WIN' THEN 1 END) as wins,
    COUNT(CASE WHEN outcome = 'LOSS' THEN 1 END) as losses,
    COALESCE(SUM(pnl), 0) as total_pnl,
    CASE
        WHEN COUNT(CASE WHEN trade_taken = true THEN 1 END) > 0
        THEN ROUND((COUNT(CASE WHEN outcome = 'WIN' THEN 1 END)::NUMERIC /
                   COUNT(CASE WHEN trade_taken = true THEN 1 END)::NUMERIC * 100), 2)
        ELSE 0
    END as win_rate_percent,
    setup_quality,
    strategy,
    event_type
FROM journal_entries
GROUP BY setup_quality, strategy, event_type;

-- Create new view for strategy performance analytics
CREATE OR REPLACE VIEW v_strategy_performance AS
SELECT
    strategy,
    event_type,
    COUNT(*) as total_signals,
    COUNT(CASE WHEN trade_taken = true THEN 1 END) as trades_taken,
    COUNT(CASE WHEN outcome = 'WIN' THEN 1 END) as wins,
    COUNT(CASE WHEN outcome = 'LOSS' THEN 1 END) as losses,
    CASE
        WHEN COUNT(CASE WHEN trade_taken = true THEN 1 END) > 0
        THEN ROUND((COUNT(CASE WHEN outcome = 'WIN' THEN 1 END)::NUMERIC /
                   COUNT(CASE WHEN trade_taken = true THEN 1 END)::NUMERIC * 100), 2)
        ELSE 0
    END as win_rate_percent,
    COALESCE(SUM(CASE WHEN trade_taken = true THEN pnl END), 0) as total_pnl,
    COALESCE(AVG(CASE WHEN trade_taken = true THEN pnl END), 0) as avg_pnl_per_trade
FROM journal_entries
WHERE strategy IS NOT NULL
GROUP BY strategy, event_type
ORDER BY total_signals DESC;

COMMENT ON VIEW v_strategy_performance IS 'Performance analytics grouped by strategy and event type';

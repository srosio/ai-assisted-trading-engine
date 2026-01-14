-- Add universal trading event schema fields to journal_entries table

-- Add strategy field to identify which strategy generated the signal
ALTER TABLE journal_entries ADD COLUMN strategy VARCHAR(100);

-- Add event_type field for standardized event types (reversal, continuation, breakout, sweep)
ALTER TABLE journal_entries ADD COLUMN event_type VARCHAR(50);

-- Add swept_level field to track price levels that were swept (for liquidity sweep strategies)
ALTER TABLE journal_entries ADD COLUMN swept_level DECIMAL(20, 8);

-- Add suggested_stop_loss field to track the stop loss suggested by the webhook
ALTER TABLE journal_entries ADD COLUMN suggested_stop_loss DECIMAL(20, 8);

-- Add indexes for performance on the new fields
CREATE INDEX idx_journal_strategy ON journal_entries(strategy);
CREATE INDEX idx_journal_event_type ON journal_entries(event_type);
CREATE INDEX idx_journal_strategy_event ON journal_entries(strategy, event_type);

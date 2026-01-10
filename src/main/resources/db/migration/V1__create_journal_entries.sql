-- Create journal_entries table for trade logging

CREATE TABLE journal_entries (
    id BIGSERIAL PRIMARY KEY,
    signal_id VARCHAR(255) NOT NULL UNIQUE,
    symbol VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    event VARCHAR(100) NOT NULL,
    webhook_payload TEXT,
    market_context TEXT,
    ai_assessment TEXT,
    rule_result TEXT,
    setup_quality VARCHAR(1),
    rules_passed BOOLEAN,
    entry_price DECIMAL(20, 8),
    stop_loss DECIMAL(20, 8),
    take_profit DECIMAL(20, 8),
    position_size DECIMAL(20, 8),
    risk_reward_ratio DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL,
    session VARCHAR(20),
    status VARCHAR(20),
    trade_taken BOOLEAN,
    exit_price DECIMAL(20, 8),
    pnl DECIMAL(20, 8),
    notes TEXT,
    outcome VARCHAR(20),
    closed_at TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_journal_signal_id ON journal_entries(signal_id);
CREATE INDEX idx_journal_symbol ON journal_entries(symbol);
CREATE INDEX idx_journal_created_at ON journal_entries(created_at);
CREATE INDEX idx_journal_setup_quality ON journal_entries(setup_quality);
CREATE INDEX idx_journal_session ON journal_entries(session);
CREATE INDEX idx_journal_outcome ON journal_entries(outcome);
CREATE INDEX idx_journal_trade_taken ON journal_entries(trade_taken);

-- Create view for today's trades
CREATE OR REPLACE VIEW v_today_trades AS
SELECT *
FROM journal_entries
WHERE DATE(created_at) = CURRENT_DATE
ORDER BY created_at DESC;

-- Create view for performance statistics
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
    setup_quality
FROM journal_entries
GROUP BY setup_quality;

COMMENT ON TABLE journal_entries IS 'Automatic journal entries for all trade signals';
COMMENT ON COLUMN journal_entries.signal_id IS 'Unique identifier for the signal';
COMMENT ON COLUMN journal_entries.webhook_payload IS 'Original webhook data from TradingView';
COMMENT ON COLUMN journal_entries.market_context IS 'Structured market context snapshot';
COMMENT ON COLUMN journal_entries.ai_assessment IS 'AI analysis result (constrained)';
COMMENT ON COLUMN journal_entries.rule_result IS 'Rule engine validation result';
COMMENT ON COLUMN journal_entries.setup_quality IS 'AI quality rating: A, B, or C';
COMMENT ON COLUMN journal_entries.rules_passed IS 'Whether all rules passed';
COMMENT ON COLUMN journal_entries.trade_taken IS 'Whether trader executed the signal (manual entry)';
COMMENT ON COLUMN journal_entries.outcome IS 'Trade outcome: WIN, LOSS, BREAKEVEN, NOT_TAKEN';

-- Games table: Core game metadata
CREATE TABLE games (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    game_type VARCHAR(50) NOT NULL,
    initial_budget DECIMAL(19, 2) NOT NULL,
    remaining_budget DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    question_timer_seconds INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_game_type CHECK (game_type IN ('MCQ_FIFO', 'MCQ_FASTEST')),
    CONSTRAINT chk_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED')),
    CONSTRAINT chk_budget_positive CHECK (initial_budget >= 0),
    CONSTRAINT chk_remaining_budget CHECK (remaining_budget >= 0 AND remaining_budget <= initial_budget),
    CONSTRAINT chk_timer_positive CHECK (question_timer_seconds > 0)
);

-- Index for status-based queries
CREATE INDEX idx_games_status ON games(status);

-- Index for scheduled games lookup
CREATE INDEX idx_games_scheduled_at ON games(scheduled_at) WHERE status = 'SCHEDULED';

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_games_updated_at
    BEFORE UPDATE ON games
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

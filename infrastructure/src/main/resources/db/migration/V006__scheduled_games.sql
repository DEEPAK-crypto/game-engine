-- Scheduled Games Tables
-- Supports automatic game start/end and question activation

CREATE TABLE scheduled_games (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL,
    scheduled_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end_time TIMESTAMP WITH TIME ZONE,
    auto_activate_questions BOOLEAN DEFAULT TRUE,
    question_interval_seconds INTEGER DEFAULT 30,
    recurrence_rule VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    last_notification_sent TIMESTAMP WITH TIME ZONE,
    job_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_scheduled_games_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

CREATE INDEX idx_scheduled_games_game_id ON scheduled_games(game_id);
CREATE INDEX idx_scheduled_games_status ON scheduled_games(status);
CREATE INDEX idx_scheduled_games_start_time ON scheduled_games(scheduled_start_time);
CREATE INDEX idx_scheduled_games_status_start ON scheduled_games(status, scheduled_start_time);

CREATE TABLE schedule_history (
    id UUID PRIMARY KEY,
    scheduled_game_id UUID NOT NULL,
    game_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    result VARCHAR(50) NOT NULL,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_schedule_history_scheduled_game FOREIGN KEY (scheduled_game_id) REFERENCES scheduled_games(id) ON DELETE CASCADE
);

CREATE INDEX idx_schedule_history_scheduled_game ON schedule_history(scheduled_game_id);
CREATE INDEX idx_schedule_history_game_id ON schedule_history(game_id);
CREATE INDEX idx_schedule_history_executed_at ON schedule_history(executed_at);

-- Add status check constraint
ALTER TABLE scheduled_games ADD CONSTRAINT chk_scheduled_game_status
    CHECK (status IN ('PENDING', 'SCHEDULED', 'STARTED', 'COMPLETED', 'CANCELLED', 'FAILED'));

ALTER TABLE schedule_history ADD CONSTRAINT chk_schedule_action
    CHECK (action IN ('GAME_STARTED', 'GAME_ENDED', 'QUESTION_ACTIVATED', 'NOTIFICATION_SENT', 'SCHEDULE_CREATED', 'SCHEDULE_CANCELLED'));

ALTER TABLE schedule_history ADD CONSTRAINT chk_action_result
    CHECK (result IN ('SUCCESS', 'FAILURE'));

-- Comment on tables
COMMENT ON TABLE scheduled_games IS 'Stores scheduled game configurations for automatic start/end';
COMMENT ON TABLE schedule_history IS 'Audit log for schedule-related actions';
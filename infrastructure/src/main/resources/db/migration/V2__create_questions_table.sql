-- Questions table: Game questions with ordering and rewards
CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    order_index INT NOT NULL,
    correct_option_id UUID,
    reward DECIMAL(19, 2) NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reward_positive CHECK (reward >= 0),
    CONSTRAINT chk_duration_positive CHECK (duration_seconds > 0),
    CONSTRAINT chk_order_index_positive CHECK (order_index >= 0),
    CONSTRAINT uq_game_order UNIQUE (game_id, order_index)
);

-- Index for game questions lookup (ordered)
CREATE INDEX idx_questions_game_id ON questions(game_id, order_index);

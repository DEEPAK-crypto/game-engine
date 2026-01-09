-- Budget transactions table: Audit trail for all budget operations
CREATE TABLE budget_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    amount DECIMAL(19, 2) NOT NULL,
    remaining_budget DECIMAL(19, 2) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    user_id UUID,
    question_id UUID REFERENCES questions(id) ON DELETE SET NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('INIT', 'DEDUCT', 'REFUND', 'AWARD')),
    CONSTRAINT chk_remaining_budget_tx CHECK (remaining_budget >= 0)
);

-- Index for game budget history lookup
CREATE INDEX idx_budget_transactions_game_id ON budget_transactions(game_id, created_at);

-- Index for user transactions lookup
CREATE INDEX idx_budget_transactions_user_id ON budget_transactions(user_id) WHERE user_id IS NOT NULL;

-- Index for question-specific transactions
CREATE INDEX idx_budget_transactions_question_id ON budget_transactions(question_id) WHERE question_id IS NOT NULL;

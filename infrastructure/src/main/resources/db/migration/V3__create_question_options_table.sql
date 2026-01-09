-- Question options table: Answer choices for each question
CREATE TABLE question_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_option_order_index_positive CHECK (order_index >= 0),
    CONSTRAINT uq_question_option_order UNIQUE (question_id, order_index)
);

-- Index for question options lookup
CREATE INDEX idx_question_options_question_id ON question_options(question_id, order_index);

-- Add FK from questions to question_options for correct_option_id
ALTER TABLE questions
    ADD CONSTRAINT fk_questions_correct_option
    FOREIGN KEY (correct_option_id)
    REFERENCES question_options(id)
    ON DELETE SET NULL;

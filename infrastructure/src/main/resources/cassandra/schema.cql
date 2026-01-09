-- Cassandra Schema for Game Platform
-- High-throughput tables for answer submissions and leaderboards

-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS game_platform
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE game_platform;

-- Turns table: All answer submissions with FIFO ordering
-- Partition by (game_id, question_id) to group all answers for a question
-- Cluster by (client_timestamp, server_sequence) for FIFO ordering
CREATE TABLE IF NOT EXISTS turns (
    game_id UUID,
    question_id UUID,
    client_timestamp TIMESTAMP,
    server_sequence BIGINT,
    turn_id UUID,
    user_id UUID,
    selected_option_id UUID,
    is_correct BOOLEAN,
    reward_amount DECIMAL,
    server_timestamp TIMESTAMP,
    PRIMARY KEY ((game_id, question_id), client_timestamp, server_sequence)
) WITH CLUSTERING ORDER BY (client_timestamp ASC, server_sequence ASC);

-- User question answers: Quick lookup for user's answer on a specific question
-- Ensures one answer per user per question
CREATE TABLE IF NOT EXISTS user_question_answers (
    user_id UUID,
    game_id UUID,
    question_id UUID,
    turn_id UUID,
    selected_option_id UUID,
    is_correct BOOLEAN,
    reward_amount DECIMAL,
    answered_at TIMESTAMP,
    PRIMARY KEY (user_id, game_id, question_id)
);

-- Question leaderboards: Top performers for each question
CREATE TABLE IF NOT EXISTS question_leaderboards (
    game_id UUID,
    question_id UUID,
    rank INT,
    user_id UUID,
    reward_amount DECIMAL,
    answered_at TIMESTAMP,
    PRIMARY KEY ((game_id, question_id), rank)
) WITH CLUSTERING ORDER BY (rank ASC);

-- Game leaderboards: Overall game rankings
CREATE TABLE IF NOT EXISTS game_leaderboards (
    game_id UUID,
    rank INT,
    user_id UUID,
    total_reward DECIMAL,
    correct_answers INT,
    PRIMARY KEY (game_id, rank)
) WITH CLUSTERING ORDER BY (rank ASC);

-- User game results: User's complete results for a game
CREATE TABLE IF NOT EXISTS user_game_results (
    user_id UUID,
    game_id UUID,
    total_reward DECIMAL,
    correct_answers INT,
    total_questions INT,
    final_rank INT,
    PRIMARY KEY (user_id, game_id)
);

-- Indexes for additional query patterns
CREATE INDEX IF NOT EXISTS idx_turns_user_id ON turns (user_id);
CREATE INDEX IF NOT EXISTS idx_turns_is_correct ON turns (is_correct);

CREATE KEYSPACE IF NOT EXISTS game_platform
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE game_platform;

-- Turns table (FIFO answer submissions)
CREATE TABLE IF NOT EXISTS turns (
    game_id uuid,
    question_id uuid,
    client_timestamp timestamp,
    server_sequence bigint,
    turn_id uuid,
    user_id uuid,
    selected_option_id uuid,
    is_correct boolean,
    reward_amount decimal,
    server_timestamp timestamp,
    PRIMARY KEY ((game_id, question_id), client_timestamp, server_sequence)
) WITH CLUSTERING ORDER BY (client_timestamp ASC, server_sequence ASC);

-- User question answers (for duplicate detection and user history)
CREATE TABLE IF NOT EXISTS user_question_answers (
    user_id uuid,
    game_id uuid,
    question_id uuid,
    turn_id uuid,
    selected_option_id uuid,
    is_correct boolean,
    reward_amount decimal,
    answered_at timestamp,
    PRIMARY KEY (user_id, game_id, question_id)
);

-- User game results (final results per user per game)
CREATE TABLE IF NOT EXISTS user_game_results (
    user_id uuid,
    game_id uuid,
    total_reward decimal,
    correct_answers int,
    total_questions int,
    final_rank int,
    PRIMARY KEY (user_id, game_id)
);
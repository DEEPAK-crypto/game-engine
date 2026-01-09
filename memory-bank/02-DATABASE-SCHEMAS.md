# Database Schemas

## PostgreSQL Schema

### Table: `games`

**Purpose**: Store game configuration and state

```sql
CREATE TABLE games (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    game_type               VARCHAR(50) NOT NULL,  -- 'MCQ_FIFO', 'MCQ_FASTEST'
    initial_budget          DECIMAL(10,2) NOT NULL,
    remaining_budget        DECIMAL(10,2) NOT NULL,
    status                  VARCHAR(50) NOT NULL,  -- 'DRAFT', 'SCHEDULED', 'ACTIVE', 'COMPLETED'
    scheduled_at            TIMESTAMP,
    started_at              TIMESTAMP,
    ended_at                TIMESTAMP,
    question_timer_seconds  INTEGER NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_scheduled_at ON games(scheduled_at) WHERE status = 'SCHEDULED';
```

**Key Columns**:
- `game_type`: Determines which AnswerEvaluator to use
- `remaining_budget`: Decremented as rewards are awarded
- `status`: State machine (DRAFT → SCHEDULED → ACTIVE → COMPLETED)
- `question_timer_seconds`: Default timer for all questions

**Constraints**:
- `remaining_budget` <= `initial_budget`
- `started_at` must be after `scheduled_at`
- `ended_at` must be after `started_at`

---

### Table: `questions`

**Purpose**: Store question content and configuration

```sql
CREATE TABLE questions (
    id                  UUID PRIMARY KEY,
    game_id             UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    question_text       TEXT NOT NULL,
    order_index         INTEGER NOT NULL,
    correct_option_id   UUID,  -- NULL until options created
    reward              DECIMAL(10,2) NOT NULL,
    duration_seconds    INTEGER NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(game_id, order_index)
);

CREATE INDEX idx_questions_game_id ON questions(game_id);
CREATE INDEX idx_questions_order ON questions(game_id, order_index);
```

**Key Columns**:
- `order_index`: Questions activate in this order (0, 1, 2, ...)
- `correct_option_id`: Foreign key to question_options (set after options created)
- `reward`: Amount deducted from game budget
- `duration_seconds`: How long players have to answer

**Business Rules**:
- Sum of all `reward` must <= `initial_budget`
- `order_index` must be sequential (0, 1, 2, ...)
- `duration_seconds` typically 15-30 seconds

---

### Table: `question_options`

**Purpose**: Multiple choice options for each question

```sql
CREATE TABLE question_options (
    id              UUID PRIMARY KEY,
    question_id     UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_text     VARCHAR(500) NOT NULL,
    order_index     INTEGER NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(question_id, order_index)
);

CREATE INDEX idx_question_options_question_id ON question_options(question_id);
```

**Key Columns**:
- `order_index`: Display order (0, 1, 2, 3 for A, B, C, D)
- `option_text`: The actual choice text

**Business Rules**:
- Typically 4 options per question
- One option's ID must match `questions.correct_option_id`

---

## Cassandra Schema

### Keyspace: `game_platform`

```cql
CREATE KEYSPACE IF NOT EXISTS game_platform
WITH replication = {
    'class': 'SimpleSnitch',
    'replication_factor': 1
};
```

**Replication Strategy**:
- Development: SimpleSnitch with RF=1
- Production: NetworkTopologyStrategy with RF=3

---

### Table: `turns`

**Purpose**: Immutable log of all answer submissions (FIFO ordering)

```cql
CREATE TABLE game_platform.turns (
    game_id             UUID,
    question_id         UUID,
    client_timestamp    TIMESTAMP,
    server_sequence     BIGINT,
    turn_id             UUID,
    user_id             UUID,
    selected_option_id  UUID,
    is_correct          BOOLEAN,
    reward_amount       DECIMAL,
    server_timestamp    TIMESTAMP,
    PRIMARY KEY ((game_id, question_id), client_timestamp, server_sequence, turn_id)
) WITH CLUSTERING ORDER BY (client_timestamp ASC, server_sequence ASC, turn_id ASC);
```

**Key Design**:
- **Partition Key**: `(game_id, question_id)` - All answers for one question in one partition
- **Clustering Keys**:
  1. `client_timestamp` - Primary ordering (FIFO)
  2. `server_sequence` - Tie-breaker (nanoseconds)
  3. `turn_id` - Ultimate uniqueness

**Why This Design?**:
- FIFO ordering naturally maintained
- All answers for a question co-located
- Efficient range queries
- Nanosecond precision prevents ties

**Query Patterns**:
```cql
-- Get all turns for a question (FIFO order)
SELECT * FROM turns
WHERE game_id = ? AND question_id = ?
ORDER BY client_timestamp ASC;

-- Get first 10 correct answers
SELECT * FROM turns
WHERE game_id = ? AND question_id = ? AND is_correct = true
LIMIT 10
ALLOW FILTERING;
```

---

### Table: `user_question_answers`

**Purpose**: Track which questions each user has answered (duplicate prevention)

```cql
CREATE TABLE game_platform.user_question_answers (
    user_id             UUID,
    game_id             UUID,
    question_id         UUID,
    turn_id             UUID,
    selected_option_id  UUID,
    is_correct          BOOLEAN,
    reward_amount       DECIMAL,
    answered_at         TIMESTAMP,
    PRIMARY KEY ((user_id, game_id), question_id)
) WITH CLUSTERING ORDER BY (question_id ASC);
```

**Key Design**:
- **Partition Key**: `(user_id, game_id)` - All of a user's answers in a game
- **Clustering Key**: `question_id` - Allows range queries

**Why This Design?**:
- Fast duplicate check: `SELECT * WHERE user_id = ? AND game_id = ? AND question_id = ?`
- User's complete game history in one partition
- Efficient reward calculations

**Query Patterns**:
```cql
-- Check if user already answered
SELECT * FROM user_question_answers
WHERE user_id = ? AND game_id = ? AND question_id = ?;

-- Get user's total rewards for a game
SELECT SUM(reward_amount) FROM user_question_answers
WHERE user_id = ? AND game_id = ?;

-- Get all questions user answered correctly
SELECT * FROM user_question_answers
WHERE user_id = ? AND game_id = ? AND is_correct = true
ALLOW FILTERING;
```

---

## Redis Data Structures

### 1. Question Leaderboard

**Key Pattern**: `leaderboard:question:{gameId}:{questionId}`

**Data Structure**: Sorted Set (ZSET)

**Members**: `userId`

**Score Calculation**:
```kotlin
score = (rewardAmount * 1e10) + (maxTimestamp - answeredAt.epochMicros)
```

**Why This Score?**:
- Primary sort: Reward amount (higher = better)
- Secondary sort: Earlier timestamp (lower = better)
- Multiplier (1e10) ensures reward dominates

**Operations**:
```redis
# Add user to leaderboard
ZADD leaderboard:question:{gameId}:{questionId} {score} {userId}

# Get user's rank (1-indexed)
ZREVRANK leaderboard:question:{gameId}:{questionId} {userId}

# Get top 10 users
ZREVRANGE leaderboard:question:{gameId}:{questionId} 0 9 WITHSCORES
```

---

### 2. Game Leaderboard

**Key Pattern**: `leaderboard:game:{gameId}`

**Data Structure**: Sorted Set (ZSET)

**Members**: `userId`

**Score Calculation**:
```kotlin
score = (totalReward * 1e10) + (maxTimestamp - lastUpdated.epochMicros)
```

**Operations**:
```redis
# Update user's total
ZADD leaderboard:game:{gameId} {score} {userId}

# Get top 100 players
ZREVRANGE leaderboard:game:{gameId} 0 99 WITHSCORES

# Get user's rank
ZREVRANK leaderboard:game:{gameId} {userId}
```

---

### 3. Active Question Cache

**Key Pattern**: `active_question:{gameId}`

**Data Structure**: String (JSON)

**Value Example**:
```json
{
  "questionId": "123e4567-e89b-12d3-a456-426614174000",
  "orderIndex": 2,
  "durationSeconds": 30,
  "expiresAt": "2026-01-10T10:30:00Z",
  "questionStartedAt": "2026-01-10T10:29:30Z"
}
```

**TTL**: Question duration + 5 seconds buffer

**Operations**:
```redis
# Set active question
SET active_question:{gameId} {json} EX {ttl}

# Get active question
GET active_question:{gameId}

# Clear when question expires
DEL active_question:{gameId}
```

---

## Data Flow Examples

### Example 1: Create Game with Questions

**PostgreSQL Writes**:
```sql
-- 1. Create game
INSERT INTO games (id, name, game_type, initial_budget, remaining_budget, status, ...)
VALUES ('game-uuid', 'Trivia Night', 'MCQ_FIFO', 1000.00, 1000.00, 'DRAFT', ...);

-- 2. Create questions
INSERT INTO questions (id, game_id, question_text, order_index, reward, duration_seconds)
VALUES
  ('q1-uuid', 'game-uuid', 'What is 2+2?', 0, 100.00, 30),
  ('q2-uuid', 'game-uuid', 'Capital of France?', 1, 200.00, 30);

-- 3. Create options
INSERT INTO question_options (id, question_id, option_text, order_index)
VALUES
  ('opt1-uuid', 'q1-uuid', '3', 0),
  ('opt2-uuid', 'q1-uuid', '4', 1),  -- correct
  ('opt3-uuid', 'q1-uuid', '5', 2),
  ('opt4-uuid', 'q1-uuid', '6', 3);

-- 4. Set correct answer
UPDATE questions SET correct_option_id = 'opt2-uuid' WHERE id = 'q1-uuid';
```

---

### Example 2: Answer Submission

**Read Operations**:
```sql
-- PostgreSQL: Get game and question
SELECT * FROM games WHERE id = 'game-uuid';
SELECT * FROM questions WHERE id = 'q1-uuid';
```

```redis
-- Redis: Get active question
GET active_question:game-uuid
```

```cql
-- Cassandra: Check duplicate
SELECT * FROM user_question_answers
WHERE user_id = 'user-uuid' AND game_id = 'game-uuid' AND question_id = 'q1-uuid';
```

**Write Operations**:
```redis
-- Redis: Add to leaderboard, get rank
ZADD leaderboard:question:game-uuid:q1-uuid 1000000000000 user-uuid
ZREVRANK leaderboard:question:game-uuid:q1-uuid user-uuid
```

```sql
-- PostgreSQL: Deduct budget (if reward awarded)
UPDATE games SET remaining_budget = remaining_budget - 100.00
WHERE id = 'game-uuid';
```

```cql
-- Cassandra: Log turn and answer
INSERT INTO turns (...) VALUES (...);
INSERT INTO user_question_answers (...) VALUES (...);
```

---

## Performance Characteristics

### PostgreSQL
- **Reads**: 100-200ms for complex joins
- **Writes**: 50-100ms for inserts/updates
- **Connections**: 100 max (HikariCP pool)
- **Indexes**: Optimized for game_id, question_id lookups

### Cassandra
- **Writes**: 10-20ms (write-optimized)
- **Reads**: 20-50ms (partition key queries)
- **Throughput**: 10K writes/sec
- **Consistency**: ONE (eventual consistency acceptable)

### Redis
- **Reads**: 1-5ms (in-memory)
- **Writes**: 1-5ms (in-memory)
- **Operations**: O(log N) for sorted sets
- **Connections**: 100 max (Lettuce pool)

---

## Backup & Recovery

### PostgreSQL
- Daily full backups
- WAL archiving for point-in-time recovery
- Replication for high availability

### Cassandra
- Snapshot backups per node
- Incremental backups
- Multi-datacenter replication

### Redis
- RDB snapshots (disabled for performance in load test)
- AOF persistence (disabled for performance in load test)
- Redis Sentinel for failover

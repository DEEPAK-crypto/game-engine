# System Flows

## Flow 1: Game Lifecycle

### State Machine

```
DRAFT ────────> SCHEDULED ────────> ACTIVE ────────> COMPLETED
  │                                     │
  └─────────────────────────────────────┴─────> ERROR (future)
```

**State Transitions**:
- `DRAFT` → `SCHEDULED`: When game is scheduled for future start
- `SCHEDULED` → `ACTIVE`: When `startGame()` is called
- `ACTIVE` → `COMPLETED`: When all questions are answered or `completeGame()` is called

---

### Step-by-Step Flow

#### **Phase 1: Game Creation (DRAFT)**

```http
POST /api/games
{
  "name": "Friday Night Trivia",
  "gameType": "MCQ_FIFO",
  "initialBudget": 1000.00,
  "questionTimerSeconds": 30
}
```

**Backend Operations**:
1. Validate budget > 0
2. Create `Game` entity with status=DRAFT
3. Save to PostgreSQL
4. Return game ID

**Database State**:
```sql
games: {
  id: uuid,
  status: 'DRAFT',
  initial_budget: 1000.00,
  remaining_budget: 1000.00,
  started_at: NULL
}
```

---

#### **Phase 2: Add Questions (Still DRAFT)**

```http
POST /api/games/{gameId}/questions
{
  "questionText": "What is 2 + 2?",
  "options": [
    {"optionText": "3"},
    {"optionText": "4"},  // correct
    {"optionText": "5"},
    {"optionText": "6"}
  ],
  "correctOptionIndex": 1,
  "reward": 100.00,
  "durationSeconds": 30
}
```

**Backend Operations**:
1. Validate game is in DRAFT status
2. Validate total rewards <= initial budget
3. Determine `order_index` (count existing questions)
4. Create `Question` entity
5. Create `QuestionOption` entities (4 options)
6. Set `correct_option_id` on question
7. Save all to PostgreSQL

**Database State**:
```sql
questions: {
  id: q1-uuid,
  game_id: game-uuid,
  order_index: 0,
  correct_option_id: opt2-uuid,
  reward: 100.00
}

question_options: [
  {id: opt1-uuid, question_id: q1-uuid, option_text: "3", order_index: 0},
  {id: opt2-uuid, question_id: q1-uuid, option_text: "4", order_index: 1},
  ...
]
```

---

#### **Phase 3: Start Game (DRAFT → ACTIVE)**

```http
POST /api/games/{gameId}/start
```

**Backend Operations**:
1. Validate game is in DRAFT status
2. Validate game has at least 1 question
3. Update status to ACTIVE
4. Set `started_at` to current timestamp
5. Save to PostgreSQL
6. **Activate first question** (see Flow 2)

**Database State**:
```sql
games: {
  id: game-uuid,
  status: 'ACTIVE',
  started_at: '2026-01-10T10:00:00Z'
}
```

```redis
# Active question cached
active_question:game-uuid = {
  "questionId": "q1-uuid",
  "orderIndex": 0,
  "durationSeconds": 30,
  "expiresAt": "2026-01-10T10:00:30Z",
  "questionStartedAt": "2026-01-10T10:00:00Z"
}
```

---

#### **Phase 4: Complete Game (ACTIVE → COMPLETED)**

```http
POST /api/games/{gameId}/complete
```

**Backend Operations**:
1. Validate game is ACTIVE
2. Update status to COMPLETED
3. Set `ended_at` to current timestamp
4. Clear active question cache
5. Save to PostgreSQL

**Database State**:
```sql
games: {
  id: game-uuid,
  status: 'COMPLETED',
  ended_at: '2026-01-10T10:10:00Z'
}
```

---

## Flow 2: Question Activation

### Automatic Activation

**Trigger**: When previous question timer expires (or game starts for first question)

**Implementation**: Background scheduler or manual API call

```http
# Manual activation (used in tests)
POST /api/games/{gameId}/questions/activate
```

**Backend Operations**:
1. Get game and verify ACTIVE status
2. Get current active question from cache
3. If active question exists and not expired, return it
4. Otherwise, get next question by `order_index`
5. Calculate `expiresAt` = now + question.durationSeconds
6. Cache in Redis with TTL
7. Return active question

**Cache Operations**:
```redis
# Set with TTL
SETEX active_question:{gameId} {ttl_seconds} {json}

# Example JSON value:
{
  "questionId": "q2-uuid",
  "orderIndex": 1,
  "durationSeconds": 30,
  "expiresAt": "2026-01-10T10:05:30Z",
  "questionStartedAt": "2026-01-10T10:05:00Z"
}
```

---

### Get Active Question

```http
GET /api/games/{gameId}/questions/active
```

**Backend Operations**:
1. Check Redis cache first
2. If cache miss, calculate from database:
   - Get game start time
   - Get all questions ordered by `order_index`
   - Calculate which question should be active now
   - Cache result
3. Return active question with timing info

**Response**:
```json
{
  "question": {
    "id": "q1-uuid",
    "questionText": "What is 2 + 2?",
    "options": [...]
  },
  "questionStartedAt": "2026-01-10T10:00:00Z",
  "expiresAt": "2026-01-10T10:00:30Z",
  "isGameEnded": false
}
```

---

## Flow 3: Answer Submission (DETAILED)

### HTTP Request

```http
POST /api/games/{gameId}/questions/submit
{
  "userId": "user-123",
  "selectedOptionId": "opt2-uuid",
  "clientTimestamp": "2026-01-10T10:00:05.123Z"
}
```

---

### Backend Processing (12 Steps)

#### **Step 1: Validate Game**
```kotlin
val game = gameRepository.findById(gameId)
  ?: throw GameNotFoundException(gameId)

if (!game.isActive()) {
  throw InvalidGameStateException("Game is not active")
}
```

---

#### **Step 2: Get Active Question (Cached)**
```kotlin
val activeResult = activeQuestionCacheService.getActiveQuestion(gameId, Instant.now())
  ?: throw NoActiveQuestionException(gameId)
```

**Redis Operation**:
```redis
GET active_question:{gameId}
```

---

#### **Step 3: Load Full Question Details**
```kotlin
val activeQuestion = questionRepository.findById(activeResult.questionId)
  ?: throw NoActiveQuestionException(gameId)
```

**PostgreSQL Query**:
```sql
SELECT * FROM questions WHERE id = ?
```

---

#### **Step 4: Check Timing Window**
```kotlin
val serverTimestamp = Instant.now()

if (serverTimestamp > activeResult.expiresAt) {
  gameMetrics.recordLateAnswer()
  throw AnswerSubmissionClosedException(activeQuestion.id)
}
```

**Why**: Enforce fair play, no late submissions

---

#### **Step 5: Check Duplicate Answer**
```kotlin
val existingAnswer = userQuestionAnswerRepository
  .findByUserIdAndGameIdAndQuestionId(userId, gameId, questionId)

if (existingAnswer != null) {
  gameMetrics.recordDuplicateAnswer()
  throw DuplicateAnswerException(userId, questionId)
}
```

**Cassandra Query**:
```cql
SELECT * FROM user_question_answers
WHERE user_id = ? AND game_id = ? AND question_id = ?
```

---

#### **Step 6: Get Evaluator (Factory Pattern)**
```kotlin
val evaluator = answerEvaluatorFactory.getEvaluator(game.gameType)
// Returns: McqFifoAnswerEvaluator for MCQ_FIFO
```

**Why**: Different game types have different rules

---

#### **Step 7: Evaluate Correctness**
```kotlin
val isCorrect = evaluator.isAnswerCorrect(activeQuestion, request)
// Checks: activeQuestion.correctOptionId == request.selectedOptionId
```

**No Database Hit**: Pure logic

---

#### **Step 8: If Correct, Atomic Leaderboard + Winner Slot Claim**
```kotlin
if (isCorrect) {
  // Atomically: add to leaderboard, get rank, claim winner slot
  // Uses Lua script to prevent race conditions
  val claimResult = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
    gameId, questionId, userId,
    answeredAt = serverTimestamp,
    maxWinners = evaluator.getMaxWinners()  // 1 for MCQ_FIFO
  )

  rank = claimResult.rank
  shouldAwardReward = claimResult.claimedWinnerSlot
}
```

**Redis Lua Script** (`claim_winner_slot.lua`):
```lua
-- KEYS[1] = leaderboard sorted set
-- KEYS[2] = winners set
-- ARGV[1] = userId, ARGV[2] = score, ARGV[3] = maxWinners

-- Step 1: Add to leaderboard
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])

-- Step 2: Get rank (0-indexed)
local rank = redis.call('ZREVRANK', KEYS[1], ARGV[1]) + 1

-- Step 3: Try to claim winner slot (atomic!)
local winnerCount = redis.call('SCARD', KEYS[2])
if rank <= maxWinners and winnerCount < maxWinners then
    local added = redis.call('SADD', KEYS[2], ARGV[1])
    if added == 1 then
        return {rank, 1, winnerCount + 1}  -- Claimed!
    end
end
return {rank, 0, winnerCount}  -- Not claimed
```

**Why Atomic?**
- Prevents race condition where two users both see rank 1
- Only one user can SADD to winners set
- Guarantees exactly N winners even with 10K concurrent users

---

#### **Step 9: Calculate Reward**
```kotlin
if (isCorrect) {
  // Reward is based on whether winner slot was claimed (not rank)
  rewardAmount = if (shouldAwardReward) activeQuestion.reward else BigDecimal.ZERO
}
```

**Note**: The Lua script handles the "first N winners" logic atomically, so we don't rely on rank-based calculation which would be racy.

---

#### **Step 10: Award Reward (If Eligible)**
```kotlin
if (shouldAwardReward) {
  // Deduct from game budget
  budgetService.awardToUser(gameId, userId, questionId, rewardAmount)

  // Update leaderboard with actual reward
  redisLeaderboardService.addToQuestionLeaderboard(
    gameId, questionId, userId,
    rewardAmount = rewardAmount,
    answeredAt = serverTimestamp
  )

  // Record metrics
  gameMetrics.recordReward(rewardAmount, gameId, userId)
}
```

**PostgreSQL Transaction**:
```sql
UPDATE games
SET remaining_budget = remaining_budget - 100.00
WHERE id = ? AND remaining_budget >= 100.00
```

**Redis Update**:
```redis
# Update with actual reward
score = (100.00 * 1e10) + (MAX_TIMESTAMP - answeredAt.epochMicros)
ZADD leaderboard:question:{gameId}:{questionId} {score} {userId}
```

---

#### **Step 11: Update Game Leaderboard**
```kotlin
if (isCorrect) {
  // Calculate user's total reward across all questions
  val userTotalReward = userQuestionAnswerRepository
    .findByUserIdAndGameId(userId, gameId)
    .sumOf { it.rewardAmount } + rewardAmount

  // Update game-level leaderboard
  redisLeaderboardService.updateGameLeaderboard(
    gameId, userId, userTotalReward, serverTimestamp
  )
}
```

**Cassandra Query**:
```cql
SELECT reward_amount FROM user_question_answers
WHERE user_id = ? AND game_id = ?
```

**Redis Update**:
```redis
score = (totalReward * 1e10) + (MAX_TIMESTAMP - serverTimestamp.epochMicros)
ZADD leaderboard:game:{gameId} {score} {userId}
```

---

#### **Step 12: Persist Turn and Answer**
```kotlin
// Log the turn (FIFO ordering)
val turn = Turn(
  gameId = gameId,
  questionId = questionId,
  clientTimestamp = request.clientTimestamp,
  serverSequence = System.nanoTime(),  // Tie-breaker
  turnId = UUID.randomUUID(),
  userId = userId,
  selectedOptionId = request.selectedOptionId,
  isCorrect = isCorrect,
  rewardAmount = rewardAmount,
  serverTimestamp = serverTimestamp
)
turnRepository.save(turn)

// Track user's answer
val userAnswer = UserQuestionAnswer(
  userId = userId,
  gameId = gameId,
  questionId = questionId,
  turnId = turn.turnId,
  selectedOptionId = request.selectedOptionId,
  isCorrect = isCorrect,
  rewardAmount = rewardAmount,
  answeredAt = serverTimestamp
)
userQuestionAnswerRepository.save(userAnswer)
```

**Cassandra Writes** (2 tables):
```cql
INSERT INTO turns (...) VALUES (...);
INSERT INTO user_question_answers (...) VALUES (...);
```

---

### Response

```json
{
  "turnId": "turn-uuid",
  "userId": "user-123",
  "questionId": "q1-uuid",
  "selectedOptionId": "opt2-uuid",
  "isCorrect": true,
  "rewardAmount": 100.00,
  "rank": 1,
  "submittedAt": "2026-01-10T10:00:05.234Z"
}
```

---

### Performance Metrics

**Total Time**: ~80-120ms

**Breakdown**:
- Step 1-3 (DB reads): 30-50ms
- Step 4-7 (validation + evaluation): 1-5ms
- Step 8-9 (Redis ops): 5-10ms
- Step 10 (budget update): 20-30ms
- Step 11 (leaderboard update): 5-10ms
- Step 12 (Cassandra writes): 10-20ms

**Bottlenecks**:
- PostgreSQL budget update (transaction)
- Cassandra sum query for total rewards

---

## Flow 4: Leaderboard Queries

### Get Question Leaderboard

```http
GET /api/games/{gameId}/questions/{questionId}/leaderboard?limit=10
```

**Backend Operations**:
```kotlin
val entries = redisLeaderboardService.getQuestionLeaderboard(
  gameId, questionId, limit = 10
)
```

**Redis Operation**:
```redis
# Get top 10 users with scores
ZREVRANGE leaderboard:question:{gameId}:{questionId} 0 9 WITHSCORES
```

**Response Time**: 5-10ms

---

### Get Game Leaderboard

```http
GET /api/games/{gameId}/leaderboard?limit=100
```

**Backend Operations**:
```kotlin
val entries = redisLeaderboardService.getGameLeaderboard(
  gameId, limit = 100
)
```

**Redis Operation**:
```redis
# Get top 100 players
ZREVRANGE leaderboard:game:{gameId} 0 99 WITHSCORES
```

**Response Time**: 5-10ms

---

## Flow 5: Complete Game Flow (E2E Test)

**Sequence** (from test-game-flow.sh):

```bash
# 1. Create game
POST /api/games → game_id

# 2. Add 5 questions (Paris, 2+2, Blue sky, Jupiter, Continents)
for q in questions:
  POST /api/games/{game_id}/questions

# 3. Start game
POST /api/games/{game_id}/start

# 4. Get active question (should be Question 1)
GET /api/games/{game_id}/questions/active

# 5. Users 1-6 answer Question 1
for user in [Alice, Bob, Carol, Dave, Eve, Frank]:
  POST /api/games/{game_id}/questions/submit

# 6. Wait 16 seconds for Question 1 to expire

# 7. Activate Question 2
POST /api/games/{game_id}/questions/activate

# 8. Users 1, 2, 7, 8 answer Question 2
for user in [Alice, Bob, Grace, Henry]:
  POST /api/games/{game_id}/questions/submit

# 9. Wait 16 seconds...

# 10. Activate Question 3
# Users 1, 3, 5 answer...

# 11. Activate Question 4
# Users 2, 4, 6 answer...

# 12. Activate Question 5
# Users 3, 7, 8 answer...

# 13. Check final leaderboard
GET /api/games/{game_id}/leaderboard

# 14. Complete game
POST /api/games/{game_id}/complete
```

**Total Time**: ~120 seconds (5 questions × 16 seconds + overhead)

**Database State After**:
- PostgreSQL: 1 game, 5 questions, 20 options
- Cassandra: ~25 turns, ~25 user_question_answers
- Redis: 1 game leaderboard, 5 question leaderboards

---

## Error Handling Flows

### Late Answer Submission

**Scenario**: User submits after question timer expires

**Flow**:
1. Check: `serverTimestamp > expiresAt`
2. Record metric: `gameMetrics.recordLateAnswer()`
3. Throw: `AnswerSubmissionClosedException`
4. HTTP 409 Conflict

---

### Duplicate Answer

**Scenario**: User tries to answer same question twice

**Flow**:
1. Query Cassandra: `SELECT * FROM user_question_answers WHERE ...`
2. If found, record metric: `gameMetrics.recordDuplicateAnswer()`
3. Throw: `DuplicateAnswerException`
4. HTTP 409 Conflict

---

### Insufficient Budget

**Scenario**: Game runs out of budget

**Flow**:
1. PostgreSQL constraint: `remaining_budget >= reward`
2. If fails, transaction rolls back
3. User still gets recorded but with $0 reward
4. Game can continue (future: auto-complete game)

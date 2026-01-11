# Race Condition Solutions for Multi-Instance Deployment

This document describes three race conditions identified in the real-time trivia game platform and their solutions.

---

## 1. Leaderboard Race Condition

**Problem:** Two users submitting correct answers simultaneously could both see Rank 1 and claim full rewards.

```
User A: ZADD leaderboard → ZREVRANK (rank 1) → award $100
User B: ZADD leaderboard → ZREVRANK (rank 1) → award $100
Result: $200 awarded instead of $100
```

**Solution:** Redis Lua Script for atomic operations

```lua
-- claim_winner_slot.lua (executed atomically on Redis server)
redis.call('ZADD', leaderboardKey, score, userId)
local rank = redis.call('ZREVRANK', leaderboardKey, userId) + 1

-- Atomic winner slot claim via SADD
if rank <= maxWinners then
    local added = redis.call('SADD', winnersKey, userId)
    if added == 1 then
        return {rank, 1}  -- Claimed winner slot
    end
end
return {rank, 0}  -- Slot already taken
```

**Why it works:** Lua scripts execute atomically on Redis - no other commands can interleave. SADD returns 1 only for the first user, guaranteeing exactly N winners.

---

## 2. Budget Race Condition

**Problem:** Concurrent requests could over-allocate game budget beyond the limit.

```
Instance A: SELECT remaining_budget → $100
Instance B: SELECT remaining_budget → $100
Instance A: UPDATE SET remaining_budget = $100 - $50 = $50
Instance B: UPDATE SET remaining_budget = $100 - $50 = $50
Result: $100 awarded but budget shows $50 remaining
```

**Solution:** Atomic conditional UPDATE

```sql
UPDATE games
SET remaining_budget = remaining_budget - :amount
WHERE id = :gameId
  AND remaining_budget >= :amount
RETURNING remaining_budget
```

**Why it works:** Single SQL statement is atomic. The WHERE clause ensures the deduction only happens if sufficient budget exists. No read-then-write pattern means no race window.

---

## 3. Duplicate Answer Race Condition

**Problem:** Same user could submit multiple answers for one question under concurrent load.

```
Request A: SELECT answer WHERE user_id = X → null (not found)
Request B: SELECT answer WHERE user_id = X → null (not found)
Request A: INSERT answer → success
Request B: INSERT answer → success (Cassandra upsert overwrites!)
Result: User submits twice, potentially claiming rewards twice
```

**Solution:** Cassandra Lightweight Transactions (LWT)

```cql
INSERT INTO user_question_answers (user_id, game_id, question_id, ...)
VALUES (?, ?, ?, ...)
IF NOT EXISTS
```

**Why it works:** LWT uses Paxos consensus to ensure atomicity. Returns `[applied: false]` if record exists. The check-and-insert happens as a single atomic operation across all Cassandra nodes.

---

## Summary

| Race Condition | Root Cause | Solution | Latency Impact |
|----------------|------------|----------|----------------|
| Leaderboard | Non-atomic rank + claim | Redis Lua Script | ~0ms |
| Budget | Read-then-write | Atomic SQL UPDATE | ~0ms |
| Duplicate Answer | Read-then-write | Cassandra LWT | +10-20ms |

**Key Principle:** Replace read-then-write patterns with atomic operations at the database level. This guarantees correctness regardless of application instance count or request timing.

---

## Additional Race Condition Fixes

### 4. Game Status Transition Race Condition

**Problem:** Two instances starting the same game simultaneously could both succeed.

```
Instance A: SELECT status = DRAFT → UPDATE status = ACTIVE
Instance B: SELECT status = DRAFT → UPDATE status = ACTIVE
Result: Both succeed, no conflict detection
```

**Solution:** Atomic conditional UPDATE with expected status check

```kotlin
// GameRepositoryImpl.transitionStatus()
UPDATE games
SET status = :newStatus,
    started_at = :timestamp,
    updated_at = NOW()
WHERE id = :gameId
  AND status = :expectedStatus
```

**Why it works:** WHERE clause includes the expected current status. Only one instance can successfully transition from DRAFT to ACTIVE. The second instance gets 0 rows updated, indicating the transition already happened.

**Implementation:**
- `GameRepository.transitionStatus()` - Atomic state transition
- `GameServiceImpl.startGame()` - Tries DRAFT→ACTIVE, then SCHEDULED→ACTIVE
- Returns clear error messages for concurrent start attempts

---

### 5. User Total Reward Race Condition

**Problem:** Two concurrent correct answers from the same user could overwrite total rewards.

```
Request A: Read user_total = $100 → Answer Q1: award $50 → Write $150
Request B: Read user_total = $100 → Answer Q2: award $30 → Write $130
Result: Only $130 stored, $50 reward lost
```

**Solution:** Atomic increment via Redis INCRBYFLOAT

```kotlin
// RedisLeaderboardServiceImpl.incrementUserTotalReward()
val newTotal = redisTemplate.opsForValue().increment(
    "user_total_reward:${gameId}:${userId}",
    rewardIncrement.toDouble()
)
```

**Why it works:** INCRBYFLOAT is atomic in Redis. No read-then-write pattern. Each reward adds correctly regardless of concurrent submissions.

**Benefits:**
- No lost updates
- Simple implementation
- Sub-millisecond performance
- Works across all application instances

---

### 6. Code Quality Fix

**Issue:** Typo in `RedisLeaderboardServiceImpl.kt:346`

```kotlin
// Before (compilation error)
private fun getUserTotalRewardKey(gameId: UUID, oderId: UUID): String {
    return "user_total_reward:$gameId:$userId"
}

// After (fixed)
private fun getUserTotalRewardKey(gameId: UUID, userId: UUID): String {
    return "user_total_reward:$gameId:$userId"
}
```

**Impact:** Parameter name `oderId` was incorrect, causing compilation issues. Fixed to `userId` for consistency.
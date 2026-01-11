--[[
Atomic leaderboard operation for claiming winner slots.

This script atomically:
1. Adds user to the leaderboard sorted set
2. Gets the user's rank
3. Attempts to claim a winner slot if eligible

KEYS:
  [1] = leaderboard key (sorted set) - e.g., "leaderboard:question:{gameId}:{questionId}"
  [2] = winners key (set) - e.g., "winners:{gameId}:{questionId}"

ARGV:
  [1] = userId (string)
  [2] = score (double) - calculated from timestamp for FIFO ordering
  [3] = maxWinners (int) - maximum number of winners allowed (e.g., 1)

RETURNS:
  Array with 3 elements:
  [1] = rank (1-indexed, integer)
  [2] = claimedSlot (1 if winner slot claimed, 0 otherwise)
  [3] = currentWinnerCount (number of winners already claimed)

Race Condition Prevention:
- SADD is atomic - only one user can successfully add to the winners set
- Even if two users get rank 1 simultaneously, only one will claim the slot
]]

local leaderboardKey = KEYS[1]
local winnersKey = KEYS[2]
local userId = ARGV[1]
local score = tonumber(ARGV[2])
local maxWinners = tonumber(ARGV[3])

-- Step 1: Add user to the leaderboard sorted set
redis.call('ZADD', leaderboardKey, score, userId)

-- Step 2: Get user's rank (0-indexed, reversed because higher scores = better)
local zeroIndexedRank = redis.call('ZREVRANK', leaderboardKey, userId)
local rank = zeroIndexedRank + 1  -- Convert to 1-indexed

-- Step 3: Check current winner count
local currentWinnerCount = redis.call('SCARD', winnersKey)

-- Step 4: Try to claim winner slot if eligible
local claimedSlot = 0

if rank <= maxWinners and currentWinnerCount < maxWinners then
    -- Atomically try to add to winners set
    -- SADD returns 1 if the element was added, 0 if it already existed
    local added = redis.call('SADD', winnersKey, userId)
    if added == 1 then
        claimedSlot = 1
        currentWinnerCount = currentWinnerCount + 1
    end
end

return {rank, claimedSlot, currentWinnerCount}
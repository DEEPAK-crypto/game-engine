# Game Platform E2E Test Script

## Overview

The `test-game-flow.sh` script is a comprehensive end-to-end test that validates the complete game lifecycle with multiple users. It tests all major features of the game platform including game creation, question management, answer submissions, leaderboards, and result tracking.

## Prerequisites

1. **Running Services**: Ensure the game service is running on `http://localhost:8080` (or set `BASE_URL` environment variable)
2. **Required Tools**:
   - `curl` - for HTTP requests
   - `jq` - for JSON parsing (install via `brew install jq` on macOS)
   - `uuidgen` - for generating user IDs (standard on macOS/Linux)

## Running the Script

### Basic Usage
```bash
./test-game-flow.sh
```

### Custom Server URL
```bash
BASE_URL=http://localhost:9090 ./test-game-flow.sh
```

### With Output Logging
```bash
./test-game-flow.sh 2>&1 | tee test-results.log
```

## What the Script Tests

### Complete Flow (16 Steps)

1. **Game Creation**
   - Creates a new MCQ_FIFO game with $1000 budget
   - Verifies game ID and initial state

2. **Question Addition**
   - Adds 3 questions with multiple-choice options
   - Each question has different reward amounts (100, 150, 200)
   - Questions: "Capital of France", "2+2", "Sky color"

3. **Question Verification**
   - Retrieves all questions for the game
   - Confirms correct count and structure

4. **Game Start**
   - Starts the game
   - Verifies status changes to ACTIVE
   - Checks budget allocation

5. **Active Question Retrieval**
   - Gets currently active question
   - Verifies timing information (start/end times, remaining seconds)

6. **Multi-User Answer Submission (Question 1)**
   - **User 1 (Alice)**: Submits correct answer FIRST → Wins reward ($100)
   - **User 2 (Bob)**: Submits correct answer second → No reward (rank 2)
   - **User 3 (Carol)**: Submits wrong answer → No reward
   - **User 4 (Dave)**: Submits correct answer third → No reward (rank 3)

7. **Question Leaderboard Check**
   - Retrieves leaderboard for Question 1
   - Verifies correct ranking and rewards

8. **Question Progression**
   - Waits for Question 1 to expire (32 seconds)
   - Verifies Question 2 becomes active

9. **Multi-User Answer Submission (Question 2)**
   - **User 2 (Bob)**: Submits correct answer FIRST → Wins reward ($150)
   - **User 1 (Alice)**: Submits correct answer second → No reward

10. **Game Leaderboard Check**
    - Verifies cumulative rewards across questions
    - Should show User 2 leading with $150, User 1 with $100

11. **Duplicate Answer Prevention**
    - User 1 attempts to answer Question 2 again
    - Verifies system rejects duplicate submission

12. **Game Completion**
    - Completes the game
    - Verifies status changes to COMPLETED
    - Checks remaining budget

13. **Final Game Leaderboard**
    - Retrieves final rankings
    - Shows total rewards for all participants

14. **Individual User Results**
    - Checks User 1's complete game results
    - Checks User 2's complete game results
    - Verifies total rewards, correct answers, and final ranks

15. **Game Details Retrieval**
    - Gets complete game information
    - Verifies all timestamps and budget information

16. **System-Wide Game List**
    - Lists all games in the system
    - Confirms test game is included

## Expected Output

The script provides color-coded output:
- 🔵 **BLUE [INFO]**: Informational messages
- 🟢 **GREEN [SUCCESS]**: Successful operations
- 🟡 **YELLOW**: Section headers
- 🔴 **RED [ERROR]**: Errors or failures

### Sample Output Structure
```
========================================
Step 1: Creating Game
========================================
[INFO] Testing against: http://localhost:8080
[SUCCESS] Game created with ID: 550e8400-e29b-41d4-a716-446655440000
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "E2E Test Trivia Game",
  "status": "DRAFT",
  ...
}

========================================
Step 6: Submitting Answers for Question 1 (Paris)
========================================
[INFO] User 1 (Alice) submitting correct answer...
[SUCCESS] User 1: isCorrect=true, reward=100.0, rank=1
[INFO] User 2 (Bob) submitting correct answer...
[SUCCESS] User 2: isCorrect=true, reward=0.0, rank=2
...
```

## Key Test Scenarios

### 1. First-In-First-Out (FIFO) Rewards
- Only the first correct answer receives the reward
- Subsequent correct answers get rank but no reward
- Tests the core MCQ_FIFO game type logic

### 2. Real-Time Leaderboards
- Question-level leaderboards update immediately
- Game-level leaderboards aggregate across questions
- Redis-based ranking with O(log N) performance

### 3. Budget Management
- Initial budget: $1000
- Question rewards: $100, $150, $200
- Remaining budget tracked after each award
- Ensures budget integrity

### 4. Duplicate Prevention
- Users can only answer each question once
- System rejects duplicate submissions
- Tests Cassandra `user_question_answers` table constraint

### 5. Multi-User Concurrency
- 4 different users participate simultaneously
- Tests race conditions in reward distribution
- Verifies FIFO ordering accuracy

### 6. Game State Machine
- DRAFT → ACTIVE → COMPLETED
- State transitions validated at each step
- Ensures proper lifecycle management

## Troubleshooting

### Script Fails with "Connection refused"
```bash
# Check if service is running
curl http://localhost:8080/actuator/health

# Start the service
./gradlew :game-service:bootRun
```

### jq Command Not Found
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq
```

### Timing Issues
If questions expire too quickly or slowly, you can modify:
- Line 63: `"questionTimerSeconds": 30` (in game creation)
- Line 290: `sleep 32` (wait time between questions)

### Test Data Cleanup
The script creates new games each run. To clean up test data:
```bash
# List all games
curl http://localhost:8080/api/games | jq '.'

# Delete specific game (if delete endpoint exists)
# curl -X DELETE http://localhost:8080/api/games/{gameId}
```

## Customization

### Modify Test Parameters

**Change number of users**: Add more USER_ID variables and submission blocks

**Change questions**: Modify the JSON in Step 2 (line 45-83)

**Change game type**: Modify `"gameType": "MCQ_FIFO"` to test other game types

**Change budget**: Modify `"initialBudget": 1000.00`

### Add Custom Validations

Add assertion blocks after any step:
```bash
if [[ "$GAME_STATUS" != "ACTIVE" ]]; then
    log_error "Expected ACTIVE status, got: $GAME_STATUS"
    exit 1
fi
```

## Integration with CI/CD

### Example GitHub Actions
```yaml
- name: Run E2E Tests
  run: |
    ./gradlew :game-service:bootRun &
    sleep 30  # Wait for service to start
    ./test-game-flow.sh
    kill %1  # Stop background service
```

### Example with Health Check
```bash
#!/bin/bash
# Wait for service to be ready
until curl -f http://localhost:8080/actuator/health; do
    echo "Waiting for service..."
    sleep 5
done

# Run tests
./test-game-flow.sh
```

## Success Criteria

The test passes when:
- ✅ All 16 steps complete without errors
- ✅ User 1 gets $100 reward from Question 1
- ✅ User 2 gets $150 reward from Question 2
- ✅ Game leaderboard shows User 2 ($150) leading User 1 ($100)
- ✅ Duplicate answer is rejected
- ✅ Game status progresses correctly: DRAFT → ACTIVE → COMPLETED
- ✅ Budget decreases correctly ($1000 → $750 after 2 rewards)

## Test Coverage

This script validates:
- ✅ REST API endpoints (10+ endpoints tested)
- ✅ PostgreSQL (games, questions, question_options)
- ✅ Cassandra (turns, user_question_answers)
- ✅ Redis (question leaderboards, game leaderboards)
- ✅ Business logic (FIFO rewards, duplicate detection)
- ✅ Metrics recording
- ✅ Request tracing (X-Trace-Id headers)

## Performance Metrics

Expected execution time:
- **Without waits**: ~2-3 seconds
- **With question expiry wait**: ~35-40 seconds

You can reduce execution time for quick tests by:
1. Removing the sleep in Step 8
2. Skipping Question 2 submission (Steps 8-9)

---

For questions or issues, check the logs or open an issue in the repository.
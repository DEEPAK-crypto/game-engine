# API Reference

Complete REST API documentation for the Game Platform.

**Base URL**: `http://localhost:8080/api`

**Content-Type**: `application/json`

---

## Game Management API

### 1. Create Game

Create a new game in DRAFT status.

**Endpoint**: `POST /api/games`

**Request Body**:
```json
{
  "name": "Friday Night Trivia",
  "gameType": "MCQ_FIFO",
  "initialBudget": 1000.00,
  "questionTimerSeconds": 30,
  "scheduledAt": "2026-01-10T20:00:00Z"  // Optional
}
```

**Validation**:
- `name`: 3-100 characters, required
- `gameType`: `MCQ_FIFO` or `MCQ_FASTEST`, required
- `initialBudget`: Must be positive, required
- `questionTimerSeconds`: 5-300 seconds, required
- `scheduledAt`: Must be in the future, optional

**Response**: `201 Created`
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Friday Night Trivia",
  "gameType": "MCQ_FIFO",
  "initialBudget": 1000.00,
  "remainingBudget": 1000.00,
  "status": "DRAFT",
  "scheduledAt": "2026-01-10T20:00:00Z",
  "startedAt": null,
  "endedAt": null,
  "questionTimerSeconds": 30,
  "createdAt": "2026-01-10T10:00:00Z",
  "updatedAt": "2026-01-10T10:00:00Z"
}
```

**Use Case**: Create a game before adding questions

---

### 2. Get Game by ID

Retrieve a specific game by its ID.

**Endpoint**: `GET /api/games/{gameId}`

**Path Parameters**:
- `gameId`: UUID of the game

**Response**: `200 OK`
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Friday Night Trivia",
  "gameType": "MCQ_FIFO",
  "initialBudget": 1000.00,
  "remainingBudget": 800.00,
  "status": "ACTIVE",
  "scheduledAt": null,
  "startedAt": "2026-01-10T10:00:00Z",
  "endedAt": null,
  "questionTimerSeconds": 30,
  "createdAt": "2026-01-10T09:00:00Z",
  "updatedAt": "2026-01-10T10:00:00Z"
}
```

**Error Responses**:
- `404 Not Found`: Game does not exist

---

### 3. Get All Games

Retrieve all games, optionally filtered by status.

**Endpoint**: `GET /api/games`

**Query Parameters**:
- `status`: Optional filter by GameStatus (`DRAFT`, `SCHEDULED`, `ACTIVE`, `COMPLETED`)

**Examples**:
```bash
# Get all games
GET /api/games

# Get only active games
GET /api/games?status=ACTIVE

# Get completed games
GET /api/games?status=COMPLETED
```

**Response**: `200 OK`
```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "name": "Friday Night Trivia",
    "status": "ACTIVE",
    ...
  },
  {
    "id": "223e4567-e89b-12d3-a456-426614174001",
    "name": "Saturday Quiz",
    "status": "DRAFT",
    ...
  }
]
```

---

### 4. Start Game

Transition a game from DRAFT or SCHEDULED to ACTIVE status.

**Endpoint**: `POST /api/games/{gameId}/start`

**Path Parameters**:
- `gameId`: UUID of the game

**Request Body** (optional):
```json
{
  "startAt": "2026-01-10T10:00:00Z"  // Default: now
}
```

**Business Rules**:
- Game must be in DRAFT or SCHEDULED status
- Game must have at least 1 question
- First question is automatically activated

**Response**: `200 OK`
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "status": "ACTIVE",
  "startedAt": "2026-01-10T10:00:00Z",
  ...
}
```

**Error Responses**:
- `404 Not Found`: Game does not exist
- `400 Bad Request`: Game is not in DRAFT/SCHEDULED status
- `400 Bad Request`: Game has no questions

**Side Effects**:
- Sets `status = ACTIVE`
- Sets `startedAt` timestamp
- Activates first question (cached in Redis)

---

### 5. Complete Game

Mark a game as completed.

**Endpoint**: `POST /api/games/{gameId}/complete`

**Path Parameters**:
- `gameId`: UUID of the game

**Business Rules**:
- Game must be in ACTIVE status

**Response**: `200 OK`
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "status": "COMPLETED",
  "startedAt": "2026-01-10T10:00:00Z",
  "endedAt": "2026-01-10T10:30:00Z",
  ...
}
```

**Side Effects**:
- Sets `status = COMPLETED`
- Sets `endedAt` timestamp
- Clears active question cache

---

### 6. Delete Game

Delete a game and all related data.

**Endpoint**: `DELETE /api/games/{gameId}`

**Path Parameters**:
- `gameId`: UUID of the game

**Response**: `204 No Content`

**Warning**: Cascades to delete all questions, options, and related data in PostgreSQL. Cassandra data (turns, answers) remains for audit purposes.

---

## Question Management API

### 7. Add Questions to Game

Add one or more questions to a game in DRAFT status.

**Endpoint**: `POST /api/games/{gameId}/questions`

**Path Parameters**:
- `gameId`: UUID of the game

**Request Body** (array of questions):
```json
[
  {
    "questionText": "What is the capital of France?",
    "options": [
      { "optionText": "London" },
      { "optionText": "Paris" },
      { "optionText": "Berlin" },
      { "optionText": "Madrid" }
    ],
    "correctOptionIndex": 1,
    "reward": 100.00,
    "durationSeconds": 30
  },
  {
    "questionText": "What is 2 + 2?",
    "options": [
      { "optionText": "3" },
      { "optionText": "4" },
      { "optionText": "5" },
      { "optionText": "6" }
    ],
    "correctOptionIndex": 1,
    "reward": 50.00,
    "durationSeconds": 15
  }
]
```

**Validation**:
- `questionText`: 10-500 characters, required
- `options`: 2-6 options, required
- `correctOptionIndex`: Must be valid index in options array
- `reward`: Non-negative, required
- `durationSeconds`: 5-300 seconds, required
- Total rewards must not exceed game's initial budget

**Response**: `201 Created`
```json
[
  {
    "id": "q1-uuid",
    "gameId": "game-uuid",
    "questionText": "What is the capital of France?",
    "orderIndex": 0,
    "options": [
      { "id": "opt1-uuid", "optionText": "London", "orderIndex": 0 },
      { "id": "opt2-uuid", "optionText": "Paris", "orderIndex": 1 },
      { "id": "opt3-uuid", "optionText": "Berlin", "orderIndex": 2 },
      { "id": "opt4-uuid", "optionText": "Madrid", "orderIndex": 3 }
    ],
    "correctOptionId": "opt2-uuid",
    "reward": 100.00,
    "durationSeconds": 30,
    "createdAt": "2026-01-10T10:00:00Z"
  },
  ...
]
```

**Business Rules**:
- Questions are assigned sequential `orderIndex` (0, 1, 2, ...)
- Questions activate in order during gameplay
- Game must be in DRAFT status

---

### 8. Get All Questions for Game

Retrieve all questions for a specific game.

**Endpoint**: `GET /api/games/{gameId}/questions`

**Path Parameters**:
- `gameId`: UUID of the game

**Response**: `200 OK`
```json
[
  {
    "id": "q1-uuid",
    "gameId": "game-uuid",
    "questionText": "What is the capital of France?",
    "orderIndex": 0,
    "options": [...],
    "correctOptionId": "opt2-uuid",
    "reward": 100.00,
    "durationSeconds": 30,
    "createdAt": "2026-01-10T10:00:00Z"
  },
  ...
]
```

**Note**: Returns questions ordered by `orderIndex`

---

### 9. Get Active Question

Retrieve the currently active question for a game.

**Endpoint**: `GET /api/games/{gameId}/questions/active`

**Path Parameters**:
- `gameId`: UUID of the game

**Response**: `200 OK`
```json
{
  "question": {
    "id": "q1-uuid",
    "gameId": "game-uuid",
    "questionText": "What is the capital of France?",
    "orderIndex": 0,
    "options": [...],
    "correctOptionId": null,  // Hidden during active play
    "reward": 100.00,
    "durationSeconds": 30,
    "createdAt": "2026-01-10T10:00:00Z"
  },
  "startTime": "2026-01-10T10:00:00Z",
  "endTime": "2026-01-10T10:00:30Z",
  "remainingSeconds": 25
}
```

**Response when no active question**: `200 OK` with `null` body

**Use Case**:
- Frontend polls this endpoint to display current question
- Clients calculate countdown timer from `remainingSeconds`

**Cache Behavior**:
- First checks Redis cache (`active_question:{gameId}`)
- If cache miss, calculates from database and caches result

---

### 10. Submit Answer

Submit a user's answer to the active question.

**Endpoint**: `POST /api/games/{gameId}/questions/submit`

**Path Parameters**:
- `gameId`: UUID of the game

**Request Body**:
```json
{
  "userId": "user-uuid",
  "selectedOptionId": "opt2-uuid",
  "clientTimestamp": "2026-01-10T10:00:05.123Z"
}
```

**Validation**:
- `userId`: UUID, required
- `selectedOptionId`: UUID, required
- `clientTimestamp`: Must not be in future, defaults to now

**Response**: `200 OK`
```json
{
  "turnId": "turn-uuid",
  "userId": "user-uuid",
  "questionId": "q1-uuid",
  "selectedOptionId": "opt2-uuid",
  "isCorrect": true,
  "rewardAmount": 100.00,
  "rank": 1,
  "submittedAt": "2026-01-10T10:00:05.234Z"
}
```

**Response Fields**:
- `turnId`: Unique identifier for this submission
- `isCorrect`: Whether answer was correct
- `rewardAmount`: Reward awarded (0 if incorrect or non-winning)
- `rank`: User's rank on question leaderboard (null if incorrect)
- `submittedAt`: Server timestamp (used for FIFO ordering)

**Error Responses**:
- `404 Not Found`: Game does not exist
- `400 Bad Request`: Game is not active
- `404 Not Found`: No active question
- `409 Conflict`: Question timer expired
- `409 Conflict`: User already answered this question

**Business Logic** (12 steps):
1. Validate game exists and is ACTIVE
2. Get active question from cache
3. Load full question details
4. Check timing window (not expired)
5. Check duplicate answer (user hasn't answered before)
6. Get evaluator for game type
7. Evaluate correctness
8. If correct: Add to leaderboard, get rank
9. Calculate reward based on rank
10. If eligible: Award reward, update budget
11. Update game leaderboard
12. Persist turn and answer to Cassandra

**Performance**: ~80-120ms average

---

### 11. Get Question by ID

Retrieve a specific question.

**Endpoint**: `GET /api/questions/{questionId}`

**Path Parameters**:
- `questionId`: UUID of the question

**Response**: `200 OK`
```json
{
  "id": "q1-uuid",
  "gameId": "game-uuid",
  "questionText": "What is the capital of France?",
  "orderIndex": 0,
  "options": [...],
  "correctOptionId": "opt2-uuid",
  "reward": 100.00,
  "durationSeconds": 30,
  "createdAt": "2026-01-10T10:00:00Z"
}
```

---

### 12. Delete Question

Delete a specific question (only if game is in DRAFT).

**Endpoint**: `DELETE /api/questions/{questionId}`

**Path Parameters**:
- `questionId`: UUID of the question

**Response**: `204 No Content`

**Business Rules**:
- Game must be in DRAFT status
- Cascades to delete all options

---

## Leaderboard API

### 13. Get Game Leaderboard

Retrieve the top players for a game.

**Endpoint**: `GET /api/leaderboards/games/{gameId}`

**Path Parameters**:
- `gameId`: UUID of the game

**Query Parameters**:
- `limit`: Number of results (default: 10, max: 100)

**Example**:
```bash
GET /api/leaderboards/games/{gameId}?limit=50
```

**Response**: `200 OK`
```json
[
  {
    "rank": 1,
    "userId": "user1-uuid",
    "totalReward": 300.00,
    "correctAnswers": 3
  },
  {
    "rank": 2,
    "userId": "user2-uuid",
    "totalReward": 200.00,
    "correctAnswers": 2
  },
  {
    "rank": 3,
    "userId": "user3-uuid",
    "totalReward": 100.00,
    "correctAnswers": 1
  }
]
```

**Ranking Algorithm**:
- Primary sort: Total reward (higher is better)
- Secondary sort: Time of last answer (earlier is better)

**Data Source**: Redis sorted set `leaderboard:game:{gameId}`

**Performance**: ~5-10ms

---

### 14. Get Question Leaderboard

Retrieve the top players for a specific question.

**Endpoint**: `GET /api/leaderboards/games/{gameId}/questions/{questionId}`

**Path Parameters**:
- `gameId`: UUID of the game
- `questionId`: UUID of the question

**Query Parameters**:
- `limit`: Number of results (default: 10, max: 100)

**Response**: `200 OK`
```json
[
  {
    "rank": 1,
    "userId": "user1-uuid",
    "rewardAmount": 100.00,
    "answeredAt": "2026-01-10T10:00:05.123Z"
  },
  {
    "rank": 2,
    "userId": "user2-uuid",
    "rewardAmount": 0.00,
    "answeredAt": "2026-01-10T10:00:05.456Z"
  }
]
```

**Note**: Only correct answers appear on leaderboard

**Data Source**: Redis sorted set `leaderboard:question:{gameId}:{questionId}`

**Use Case**: Display who answered fastest for a question

---

### 15. Get User Game Result

Retrieve a user's final results for a game.

**Endpoint**: `GET /api/leaderboards/users/{userId}/games/{gameId}`

**Path Parameters**:
- `userId`: UUID of the user
- `gameId`: UUID of the game

**Response**: `200 OK`
```json
{
  "userId": "user1-uuid",
  "gameId": "game-uuid",
  "totalReward": 300.00,
  "correctAnswers": 3,
  "totalQuestions": 5,
  "finalRank": 1
}
```

**Response when user didn't play**: `200 OK` with `null` body

**Data Source**: Cassandra `user_game_results` table

**Use Case**: Show user their performance after game ends

---

## Error Responses

All error responses follow this format:

```json
{
  "timestamp": "2026-01-10T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Game is not in DRAFT status",
  "path": "/api/games/123e4567-e89b-12d3-a456-426614174000/questions"
}
```

### Common HTTP Status Codes

- `200 OK`: Request succeeded
- `201 Created`: Resource created successfully
- `204 No Content`: Delete succeeded
- `400 Bad Request`: Invalid request data or business rule violation
- `404 Not Found`: Resource does not exist
- `409 Conflict`: State conflict (duplicate answer, expired timer)
- `422 Unprocessable Entity`: Validation failed
- `500 Internal Server Error`: Server error

### Common Error Scenarios

**GameNotFoundException** (`404`):
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Game not found with ID: 123e4567-e89b-12d3-a456-426614174000"
}
```

**InvalidGameStateException** (`400`):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Game is not active"
}
```

**DuplicateAnswerException** (`409`):
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "User user-uuid has already answered question q1-uuid"
}
```

**AnswerSubmissionClosedException** (`409`):
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Submission window closed for question q1-uuid"
}
```

**ValidationException** (`422`):
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Validation failed",
  "errors": [
    {
      "field": "name",
      "message": "Game name must be between 3 and 100 characters"
    },
    {
      "field": "initialBudget",
      "message": "Initial budget must be positive"
    }
  ]
}
```

---

## Rate Limiting

**Current State**: ⚠️ No rate limiting implemented

**Future**:
- Per-user limits (e.g., 100 requests/minute)
- Per-IP limits for anonymous requests
- Exponential backoff for repeated failures

---

## Authentication

**Current State**: ⚠️ No authentication required

All endpoints are publicly accessible. The `userId` in answer submissions is taken from the request body without verification.

**Future**:
- JWT-based authentication
- OAuth2 integration
- API key for load testing clients

---

## Performance Characteristics

### Read Endpoints

- **GET /api/games/{gameId}**: 20-50ms (PostgreSQL)
- **GET /api/games/{gameId}/questions/active**: 5-10ms (Redis cache)
- **GET /api/leaderboards/**: 5-10ms (Redis)

### Write Endpoints

- **POST /api/games**: 50-100ms (PostgreSQL)
- **POST /api/games/{gameId}/questions**: 100-200ms (PostgreSQL, multiple tables)
- **POST /api/games/{gameId}/questions/submit**: 80-120ms (PostgreSQL + Cassandra + Redis)

### Bottlenecks

- PostgreSQL budget update (requires transaction)
- Cassandra sum query for user total rewards
- Connection pool exhaustion under heavy load

---

## Testing the API

### Using cURL

```bash
# Create a game
GAME_RESPONSE=$(curl -s -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Game",
    "gameType": "MCQ_FIFO",
    "initialBudget": 1000.00,
    "questionTimerSeconds": 30
  }')
GAME_ID=$(echo $GAME_RESPONSE | jq -r '.id')

# Add questions
curl -X POST http://localhost:8080/api/games/$GAME_ID/questions \
  -H "Content-Type: application/json" \
  -d '[{
    "questionText": "What is 2 + 2?",
    "options": [
      {"optionText": "3"},
      {"optionText": "4"},
      {"optionText": "5"},
      {"optionText": "6"}
    ],
    "correctOptionIndex": 1,
    "reward": 100.00,
    "durationSeconds": 30
  }]'

# Start game
curl -X POST http://localhost:8080/api/games/$GAME_ID/start

# Submit answer
USER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -X POST http://localhost:8080/api/games/$GAME_ID/questions/submit \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"selectedOptionId\": \"<option-id>\",
    \"clientTimestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\"
  }"

# Get leaderboard
curl http://localhost:8080/api/leaderboards/games/$GAME_ID?limit=10
```

### Using the E2E Test Script

```bash
# Run the complete E2E test
./test-game-flow.sh
```

This script:
- Creates a game with 5 questions
- Starts the game
- Simulates 8 users answering questions
- Validates leaderboards
- Completes the game

---

## WebSocket Support (Future)

**Planned Endpoints**:

```
ws://localhost:8080/ws/games/{gameId}
```

**Events**:
- `question.activated`: New question is active
- `question.answered`: Someone answered (show count)
- `question.expired`: Question timer expired
- `game.completed`: Game ended
- `leaderboard.updated`: Real-time rank changes

**Use Case**: Real-time updates for all connected clients without polling

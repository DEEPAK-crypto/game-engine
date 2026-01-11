# System Architecture

## High-Level Architecture

```
┌─────────────┐
│   Clients   │  (Web/Mobile/Load Tests)
└──────┬──────┘
       │ HTTP REST
       │
┌──────▼──────────────────────────────────────────┐
│          Spring Boot Application                │
│  ┌────────────────────────────────────────┐   │
│  │  REST Controllers (QuestionController)  │   │
│  └────────────┬───────────────────────────┘   │
│               │                                 │
│  ┌────────────▼───────────────────────────┐   │
│  │  Service Layer                         │   │
│  │  - AnswerSubmissionService             │   │
│  │  - GameLifecycleService                │   │
│  │  - BudgetService                       │   │
│  │  - ActiveQuestionCacheService          │   │
│  └────────────┬───────────────────────────┘   │
│               │                                 │
│  ┌────────────▼───────────────────────────┐   │
│  │  Evaluation Layer (NEW!)               │   │
│  │  - AnswerEvaluatorFactory              │   │
│  │  - McqFifoAnswerEvaluator              │   │
│  └────────────┬───────────────────────────┘   │
└───────────────┼─────────────────────────────────┘
                │
       ┌────────┴────────┬────────────────┐
       │                 │                │
┌──────▼──────┐   ┌─────▼──────┐  ┌─────▼─────┐
│ PostgreSQL  │   │ Cassandra  │  │   Redis   │
│  (Primary)  │   │  (Writes)  │  │ (Cache +  │
│             │   │            │  │  L'board) │
└─────────────┘   └────────────┘  └───────────┘
```

## Multi-Module Architecture

### 1. **core** Module
**Purpose**: Shared domain models and enums

**Contents**:
- `Game`, `Question`, `QuestionOption` - Domain entities
- `GameType`, `GameStatus` - Enums
- `Turn`, `UserQuestionAnswer` - Event models
- No dependencies on other modules
- Pure Kotlin data classes

**Why**: Prevents circular dependencies, allows reuse across modules

### 2. **infrastructure** Module
**Purpose**: Database schemas and migrations

**Contents**:
- **PostgreSQL**:
  - Flyway migrations (V1__*, V2__*, V3__*)
  - JOOQ code generation
  - Tables: games, questions, question_options
- **Cassandra**:
  - Schema CQL files
  - Keyspace: game_platform
  - Tables: turns, user_question_answers

**Why**: Centralized data layer, version-controlled schemas

### 3. **game-service** Module
**Purpose**: Main application logic

**Contents**:
- REST API endpoints
- Business services
- Answer evaluation strategies (Factory pattern)
- Configuration
- Gatling load tests

**Dependencies**: core, infrastructure

## Data Store Responsibilities

### PostgreSQL (Relational)
**Role**: Source of truth for game configuration

**Tables**:
- `games` - Game metadata, budgets, status
- `questions` - Question text, correct answers, rewards
- `question_options` - Multiple choice options

**Characteristics**:
- ACID transactions
- Complex queries (joins, aggregations)
- Moderate read/write volume
- Connection pool: 100 connections

### Cassandra (Wide-Column)
**Role**: High-throughput event log

**Tables**:
- `turns` - Every answer submission, FIFO ordering
- `user_question_answers` - User answer history

**Characteristics**:
- Write-optimized (10K writes/sec)
- Time-series data
- No joins needed
- Partition key: (user_id, game_id)
- Clustering: question_id for ordering

**Why Cassandra?**:
- Handles massive concurrent writes
- Linear scalability
- No single point of failure
- Optimized for time-series queries

### Redis (In-Memory)
**Role**: Real-time caching and leaderboards

**Data Structures**:
- **Sorted Sets** (Leaderboards):
  - Key: `leaderboard:question:{gameId}:{questionId}`
  - Key: `leaderboard:game:{gameId}`
  - Score: Combined (reward * 1e10) + timestamp
  - O(log N) updates, O(log N) rank queries

- **Sets** (Winner Tracking):
  - Key: `winners:{gameId}:{questionId}`
  - Members: userIds who claimed winner slots
  - Used by Lua script for atomic winner slot claiming

- **Strings** (Cache):
  - Key: `active_question:{gameId}`
  - Value: JSON of active question timing
  - TTL: Question duration

**Lua Scripts**:
- `claim_winner_slot.lua` - Atomic leaderboard entry + winner slot claim
  - Prevents race conditions in distributed deployments
  - Located at: `game-service/src/main/resources/redis/`

**Characteristics**:
- Sub-millisecond reads
- Atomic operations via Lua scripts
- Built-in sorted sets for rankings
- Connection pool: 100 connections
- Max memory: 1GB (LRU eviction)

## Design Patterns

### 1. Strategy Pattern (Answer Evaluation)
```kotlin
interface AnswerEvaluator {
    fun isAnswerCorrect(question, request): Boolean
    fun calculateReward(question, userRank): RewardEvaluationResult
}

class McqFifoAnswerEvaluator : AnswerEvaluator { ... }
class McqFastestAnswerEvaluator : AnswerEvaluator { ... } // Future
```

**Why**: Different game types have different reward rules

### 2. Factory Pattern (Evaluator Selection)
```kotlin
class AnswerEvaluatorFactory {
    fun getEvaluator(gameType: GameType): AnswerEvaluator
}
```

**Why**: Decouple game type from evaluation logic

### 3. Repository Pattern (Data Access)
```kotlin
interface GameRepository {
    fun findById(id: UUID): Game?
    fun save(game: Game): Game
}
```

**Why**: Abstract database operations, easy testing

### 4. Service Layer Pattern (Business Logic)
```kotlin
class AnswerSubmissionServiceImpl : AnswerSubmissionService {
    @Transactional
    fun submitAnswer(gameId, request): AnswerSubmissionResponse
}
```

**Why**: Encapsulate complex workflows, transaction management

## Key Architectural Decisions

### 1. **Polyglot Persistence**
**Decision**: Use PostgreSQL + Cassandra + Redis

**Rationale**:
- PostgreSQL: Complex queries, ACID for game state
- Cassandra: Write scalability for answer streams
- Redis: Low-latency leaderboards

**Trade-off**: Increased operational complexity vs. performance

### 2. **Server-Side Timestamps**
**Decision**: Use server timestamp for FIFO ordering, not client

**Rationale**:
- Client clocks are unreliable
- Prevents clock manipulation
- Fair for all players

**Implementation**: `Instant.now()` + `System.nanoTime()` for ties

### 3. **Two-Phase Answer Evaluation**
**Decision**: Check correctness first, then calculate reward

**Rationale**:
- Only correct answers on leaderboard
- Cleaner separation of concerns
- Easier to add new game types

**Flow**:
1. `evaluator.isAnswerCorrect()` → Boolean
2. If correct: Add to leaderboard, get rank
3. `evaluator.calculateReward(rank)` → RewardEvaluationResult

### 4. **Active Question Caching**
**Decision**: Cache active question in Redis

**Rationale**:
- Reduce PostgreSQL load (10K reads/sec)
- Sub-millisecond response times
- Automatic expiration with TTL

**Cache Key**: `active_question:{gameId}`

### 5. **Cassandra for Answer Log**
**Decision**: Store all answer submissions in Cassandra

**Rationale**:
- Write throughput (10K concurrent users)
- Audit trail for disputes
- Time-series optimized storage

**Schema**: Partition by (user_id, game_id), cluster by question_id

### 6. **Lua Script for Atomic Winner Slot Claiming**
**Decision**: Use Redis Lua script for atomic leaderboard + winner slot operations

**Problem**:
In a multi-instance deployment, two users submitting simultaneously could both:
1. Get added to leaderboard
2. Both see rank 1
3. Both claim full rewards

**Solution**:
Lua script (`claim_winner_slot.lua`) atomically:
1. Adds user to sorted set (ZADD)
2. Gets user's rank (ZREVRANK)
3. Claims winner slot if eligible (SADD to winners set)

```lua
-- Atomic: only one user can claim each winner slot
local added = redis.call('SADD', winnersKey, oderId)
if added == 1 then
    return {rank, 1, winnerCount}  -- Claimed slot
end
return {rank, 0, winnerCount}  -- Slot already taken
```

**Why Lua?**:
- Executes atomically on Redis server
- No network round-trips between operations
- Prevents race conditions across multiple app instances
- Sub-millisecond execution

**Trade-off**: Logic in Lua vs. application code, but guarantees correctness

### 7. **Graceful Shutdown**
**Decision**: Implement graceful shutdown with request tracking

**Rationale**:
- Complete in-flight requests before shutdown
- Prevent data loss during deployments
- Allow health checks during shutdown for orchestrator probes

**Implementation**:
- `server.shutdown: graceful` in Spring Boot
- 30-second timeout for shutdown phase
- `GracefulShutdownFilter` tracks active requests
- Returns 503 with `Retry-After` header for new requests during shutdown

## Scalability Considerations

### Horizontal Scaling
- **Stateless Application**: Can run multiple instances
- **Load Balancer**: Distribute traffic across instances
- **Database Clustering**: Cassandra multi-node, PostgreSQL read replicas

### Vertical Scaling
- **Connection Pools**: Tuned for workload (100 connections)
- **JVM Heap**: Sufficient for object allocation
- **Database Resources**: CPU/RAM sized for concurrent load

### Performance Optimizations
- **Redis Leaderboards**: O(log N) operations
- **Cassandra Batching**: Efficient writes
- **PostgreSQL Indexes**: Fast lookups on game_id, question_id
- **Connection Pooling**: HikariCP with optimal settings

## Security Considerations

### Current State
- ⚠️ **No Authentication**: Anyone can submit answers
- ⚠️ **No Rate Limiting**: Vulnerable to spam
- ⚠️ **No Input Validation**: Basic validation only

### Future Enhancements
- JWT-based authentication
- Rate limiting per user
- Input sanitization
- HTTPS enforcement
- SQL injection prevention (parameterized queries)

## Monitoring & Observability

### Metrics (Micrometer)
- Request rates, latencies, error rates
- Connection pool stats
- Cache hit rates
- Business metrics (answers/sec, rewards awarded)

### Logging
- Structured JSON logs (logback)
- Request/response correlation IDs
- Error stack traces
- Performance markers

### Future
- Distributed tracing (Zipkin/Jaeger)
- APM (Application Performance Monitoring)
- Alerting (PagerDuty/Slack)

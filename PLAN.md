# Game Platform Implementation Plan

## Overview
Build a distributed trivia game platform with PostgreSQL + Cassandra databases, JOOQ code generation, and Spring Boot 3.x.

**Package**: `com.gameplatform.game`
**Structure**: Multi-module Gradle project

---

## Module Structure

```
game-engine/
├── build.gradle.kts (root)
├── settings.gradle.kts
├── docker-compose.yml
├── core/                          # Domain models, utilities
│   └── build.gradle.kts
├── infrastructure/                # Database configs, JOOQ, Cassandra
│   └── build.gradle.kts
└── game-service/                  # Service layer, REST API
    └── build.gradle.kts
```

---

## Milestone 1: Project Foundation ✅
**Commit message**: `feat: M1 - Project foundation with multi-module Gradle setup`

### Tasks
1. ✅ Update root `build.gradle.kts` with Spring Boot, Kotlin, JOOQ plugins
2. ✅ Update `settings.gradle.kts` with module includes
3. ✅ Create `docker-compose.yml` with PostgreSQL, Cassandra, Redis
4. ✅ Create `core/build.gradle.kts` - minimal dependencies
5. ✅ Create `infrastructure/build.gradle.kts` - JOOQ, Cassandra, Flyway
6. ✅ Create `game-service/build.gradle.kts` - Spring Boot, Web dependencies
7. ✅ Create base package structure in each module
8. ✅ Create application.yml configurations

---

## Milestone 2: Database Schemas
**Commit message**: `feat: M2 - PostgreSQL and Cassandra database schemas`

### Tasks
1. Create Flyway migrations for PostgreSQL tables:
   - `V1__create_games_table.sql`
   - `V2__create_questions_table.sql`
   - `V3__create_question_options_table.sql`
   - `V4__create_budget_transactions_table.sql`
2. Create Cassandra CQL schema:
   - `cassandra/schema.cql` (turns, user_question_answers, leaderboards)
3. Create Cassandra initialization script for Docker

### Files to Create
- `infrastructure/src/main/resources/db/migration/V1__create_games_table.sql`
- `infrastructure/src/main/resources/db/migration/V2__create_questions_table.sql`
- `infrastructure/src/main/resources/db/migration/V3__create_question_options_table.sql`
- `infrastructure/src/main/resources/db/migration/V4__create_budget_transactions_table.sql`
- `infrastructure/src/main/resources/cassandra/schema.cql`

---

## Milestone 3: Domain Models
**Commit message**: `feat: M3 - Domain models and ActiveQuestionCalculator`

### Tasks
1. Create core domain enums: `GameStatus`, `GameType`, `TransactionType`
2. Create domain models: `Game`, `Question`, `QuestionOption`, `BudgetTransaction`
3. Create `QuestionTiming` and `ActiveQuestionResult` data classes
4. Implement `ActiveQuestionCalculator` object with time-based logic
5. Create Cassandra entity classes with Spring Data annotations:
   - `Turn`, `UserQuestionAnswer`, `QuestionLeaderboard`, `GameLeaderboard`, `UserGameResult`
6. Write unit tests for `ActiveQuestionCalculator`

### Files to Create
- `core/src/main/kotlin/com/gameplatform/game/domain/enums/GameStatus.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/enums/GameType.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/enums/TransactionType.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/Game.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/Question.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/QuestionOption.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/BudgetTransaction.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/QuestionTiming.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/model/ActiveQuestionResult.kt`
- `core/src/main/kotlin/com/gameplatform/game/domain/calculator/ActiveQuestionCalculator.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/entity/Turn.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/entity/UserQuestionAnswer.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/entity/QuestionLeaderboard.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/entity/GameLeaderboard.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/entity/UserGameResult.kt`
- `core/src/test/kotlin/com/gameplatform/game/domain/calculator/ActiveQuestionCalculatorTest.kt`

---

## Milestone 4: Repository Layer
**Commit message**: `feat: M4 - JOOQ and Cassandra repository implementations`

### Tasks
1. Configure JOOQ code generation in infrastructure module
2. Create PostgreSQL repository interfaces and implementations:
   - `GameRepository` / `GameRepositoryImpl`
   - `QuestionRepository` / `QuestionRepositoryImpl`
   - `QuestionOptionRepository` / `QuestionOptionRepositoryImpl`
   - `BudgetTransactionRepository` / `BudgetTransactionRepositoryImpl`
3. Create Cassandra repositories (Spring Data):
   - `TurnRepository`
   - `UserQuestionAnswerRepository`
   - `QuestionLeaderboardRepository`
   - `GameLeaderboardRepository`
   - `UserGameResultRepository`
4. Create database configuration classes
5. Write integration tests with Testcontainers

### Files to Create
- `infrastructure/src/main/kotlin/com/gameplatform/game/config/PostgresConfig.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/config/CassandraConfig.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/GameRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/impl/GameRepositoryImpl.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/QuestionRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/impl/QuestionRepositoryImpl.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/QuestionOptionRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/impl/QuestionOptionRepositoryImpl.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/BudgetTransactionRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/repository/impl/BudgetTransactionRepositoryImpl.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/repository/TurnRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/repository/UserQuestionAnswerRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/repository/QuestionLeaderboardRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/repository/GameLeaderboardRepository.kt`
- `infrastructure/src/main/kotlin/com/gameplatform/game/cassandra/repository/UserGameResultRepository.kt`

---

## Milestone 5: Service Layer
**Commit message**: `feat: M5 - Service layer with business logic`

### Tasks
1. Create service interfaces and implementations:
   - `GameService` - game CRUD, status management
   - `QuestionService` - question management, active question lookup
   - `AnswerSubmissionService` - answer validation, FIFO processing
   - `BudgetService` - budget management with SERIALIZABLE transactions
   - `LeaderboardService` - leaderboard updates and queries
2. Implement error handling with domain exceptions
3. Create DTOs for service layer communication
4. Write unit tests for services

### Files to Create
- `game-service/src/main/kotlin/com/gameplatform/game/service/GameService.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/impl/GameServiceImpl.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/QuestionService.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/impl/QuestionServiceImpl.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/AnswerSubmissionService.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/impl/AnswerSubmissionServiceImpl.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/BudgetService.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/impl/BudgetServiceImpl.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/LeaderboardService.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/service/impl/LeaderboardServiceImpl.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/exception/DomainExceptions.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/dto/*.kt`

---

## Milestone 6: REST API Layer
**Commit message**: `feat: M6 - REST API controllers and validation`

### Tasks
1. Create REST controllers:
   - `GameController` - game management endpoints
   - `QuestionController` - active question, submit answer
   - `LeaderboardController` - leaderboard queries
2. Create request/response DTOs with validation
3. Implement global exception handler
4. Add OpenAPI documentation annotations
5. Create Spring Boot main application class

### Files to Create
- `game-service/src/main/kotlin/com/gameplatform/game/GameServiceApplication.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/controller/GameController.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/controller/QuestionController.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/controller/LeaderboardController.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/controller/dto/*.kt`
- `game-service/src/main/kotlin/com/gameplatform/game/exception/GlobalExceptionHandler.kt`

---

## Milestone 7: Integration Tests
**Commit message**: `feat: M7 - Comprehensive integration tests with Testcontainers`

### Tasks
1. Set up Testcontainers configuration for PostgreSQL and Cassandra
2. Create integration tests for repositories
3. Create integration tests for services
4. Create integration tests for API endpoints
5. Set up test data builders/factories

### Files to Create
- `game-service/src/test/kotlin/com/gameplatform/game/testconfig/TestcontainersConfig.kt`
- `game-service/src/test/kotlin/com/gameplatform/game/repository/*Test.kt`
- `game-service/src/test/kotlin/com/gameplatform/game/service/*Test.kt`
- `game-service/src/test/kotlin/com/gameplatform/game/controller/*Test.kt`
- `game-service/src/test/kotlin/com/gameplatform/game/testutil/TestDataFactory.kt`

---

## Verification Plan

After each milestone:
1. Run `./gradlew build` to verify compilation
2. Run `./gradlew test` to verify tests pass
3. For M2+: Start Docker containers and verify database connectivity
4. For M6+: Start application and test endpoints with curl/httpie

### End-to-End Test Flow
```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build and run
./gradlew :game-service:bootRun

# 3. Create a game
curl -X POST http://localhost:8080/api/games -H "Content-Type: application/json" -d '{
  "name": "Test Trivia",
  "gameType": "MCQ_FIFO",
  "initialBudget": 1000,
  "questionTimerSeconds": 30
}'

# 4. Add questions, start game, submit answers, check leaderboard
```

---

## Dependencies Summary

### Root build.gradle.kts
- Spring Boot 3.2.x plugin
- Kotlin 2.0.x plugin
- JOOQ plugin
- Flyway plugin

### Core module
- Kotlin stdlib
- Jackson annotations

### Infrastructure module
- Spring Boot Data JDBC
- Spring Boot Data Cassandra
- JOOQ
- PostgreSQL driver
- Flyway

### Game-service module
- Spring Boot Web
- Spring Boot Validation
- Spring Boot Actuator
- Testcontainers (test)

---

## Architecture Principles

### 1. Distributed FIFO Ordering
**Problem**: In distributed systems, multiple game-service instances receive concurrent answer submissions. Must ensure fair FIFO ordering.

**Solution**: Hybrid timestamp approach
- Client sends `clientTimestamp` with submission
- Server adds `serverSequence` (monotonically increasing per node)
- Cassandra clustering key: `(client_timestamp, server_sequence)`
- Provides total ordering: client time is primary, server sequence breaks ties

### 2. Calculated Active Question (Stateless Design)
**Problem**: Traditional approaches use background jobs/SQS to transition questions, causing race conditions.

**Solution**: Pure time-based calculation
- No stored "current question" state
- Algorithm calculates active question from `game.started_at` + question durations
- Example: Game starts 10:00:00, questions are [Q1: 30s, Q2: 30s, Q3: 30s]
  - At 10:00:45 → elapsed 45s → Q2 is active (30-60s window)

### 3. Budget Management
- Game has fixed `initial_budget` (e.g., $1000)
- Each question has `reward` amount (deducted when question starts)
- First correct answer wins the reward
- PostgreSQL SERIALIZABLE transactions prevent over-allocation

### 4. Two-Database Strategy
**PostgreSQL (Source of Truth)**:
- Games, questions, question_options (metadata)
- Budget transactions (needs ACID)

**Cassandra (High Throughput)**:
- Turns (answer submissions) - millions of writes
- User answers, leaderboards, game results
- Optimized for write-heavy workloads

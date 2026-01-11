# Game Platform - Project Overview

## What is This Project?

A **real-time multiplayer trivia game platform** where users compete to answer multiple-choice questions. Think of it like a live quiz competition where speed and accuracy matter!

## Core Concept

- **Host creates a game** with multiple questions and a prize budget
- **Players join** and compete in real-time
- **Questions appear one at a time** with a countdown timer
- **First correct answer wins the reward** (MCQ_FIFO mode)
- **Leaderboard tracks** who's winning throughout the game

## Key Features

### 1. Game Types
- **MCQ_FIFO**: First correct answer wins 100% of the reward
- **MCQ_FASTEST**: (Future) Fastest responders get tiered rewards

### 2. Real-Time Gameplay
- Questions activate at scheduled times
- Players have limited time to answer (e.g., 15-30 seconds)
- Instant feedback on correctness and ranking
- Live leaderboard updates

### 3. Reward System
- Each question has a reward amount
- Budget is deducted from the game pool
- Only correct answers eligible for rewards
- First N users can win (currently N=1, configurable)

### 4. Fair Play
- Server-side timestamp for FIFO ordering
- Nanosecond precision for tie-breaking
- Duplicate answer prevention
- Late submission detection

## Technology Stack

### Backend
- **Kotlin** + **Spring Boot** - Main application framework
- **PostgreSQL** - Relational data (games, questions, users)
- **Cassandra** - High-throughput writes (turns, answers)
- **Redis** - Real-time leaderboards and caching
- **Gradle** - Multi-module build system

### Performance & Testing
- **Gatling** - Load testing (10K concurrent users)
- **Docker Compose** - Local infrastructure
- **Micrometer** - Metrics and monitoring

## Project Structure

```
game-engine/
├── core/                    # Domain models and enums
├── infrastructure/          # Database schemas (PostgreSQL, Cassandra)
├── game-service/           # Main service logic
│   ├── src/main/kotlin/
│   │   ├── api/           # REST endpoints
│   │   ├── service/       # Business logic
│   │   ├── evaluation/    # Answer evaluation strategies
│   │   └── config/        # Configuration
│   └── src/gatling/       # Load tests
└── memory-bank/           # This documentation!
```

## Quick Stats

- **Performance Target**: 10,000 concurrent users
- **Database Pools**: 100 PostgreSQL connections, 100 Redis connections
- **Response Time**: < 100ms for answer submissions
- **Throughput**: 10,000 requests/second
- **Question Timer**: 15-30 seconds typical

## Use Cases

1. **Live Quiz Shows**: Host competitions with cash prizes
2. **Educational Games**: Teachers create quizzes for students
3. **Corporate Training**: Gamified learning assessments
4. **Pub Trivia**: Digital trivia nights with remote players

## What Makes This Interesting?

- **High Concurrency**: Thousands of users answering simultaneously
- **Fairness**: Nanosecond-precision FIFO ordering
- **Scalability**: Cassandra for write-heavy workloads
- **Real-Time**: Redis-powered instant leaderboards
- **Extensibility**: Factory pattern for different game types

## Current Status

- ✅ Core game lifecycle (create, start, complete)
- ✅ Question activation and timing
- ✅ Answer submission with ranking
- ✅ FIFO reward distribution
- ✅ Real-time leaderboards
- ✅ Load testing framework
- ✅ Factory pattern for game types
- ✅ Atomic winner slot claiming (Lua script - prevents race conditions)
- ✅ Graceful shutdown support
- 🚧 MCQ_FASTEST implementation
- 🚧 WebSocket for real-time updates
- 🚧 User authentication

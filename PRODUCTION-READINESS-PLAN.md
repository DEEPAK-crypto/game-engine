# Production Readiness Plan - Game Platform

## Executive Summary

This document outlines the complete plan to transform the Game Platform from a development-ready application into a fully production-ready, self-sufficient system with multi-pod deployment, monitoring, alerting, automated game lifecycle management, and all necessary production features.

---

## Current State Assessment

### What Exists
- Kotlin/Spring Boot multi-module application
- Polyglot persistence (PostgreSQL, Cassandra, Redis)
- Docker Compose for local development
- Prometheus metrics endpoint exposed (`/actuator/prometheus`)
- Graceful shutdown support
- Race condition handling with atomic operations
- Load testing framework (Gatling)

### What's Missing
- No Dockerfile for the application
- No Kubernetes manifests
- No monitoring stack (Prometheus/Grafana)
- No alerting system
- No game scheduling/auto-start/end
- No authentication/authorization
- No rate limiting
- No WebSocket real-time updates
- No CI/CD pipeline
- No distributed tracing

---

## Implementation Plan

### Phase 1: Containerization & Basic Kubernetes Deployment

#### 1.1 Application Dockerfile
**File: `Dockerfile`**

Create a multi-stage Dockerfile for the Spring Boot application:
- Stage 1: Gradle build with caching
- Stage 2: JRE runtime image (eclipse-temurin:17-jre-alpine)
- Non-root user for security
- Health check configuration
- Optimized JVM flags for containers

#### 1.2 Kubernetes Base Manifests
**Directory: `k8s/base/`**

| File | Purpose |
|------|---------|
| `namespace.yaml` | Dedicated namespace for the platform |
| `configmap.yaml` | Application configuration (non-sensitive) |
| `secret.yaml` | Database credentials, API keys |
| `deployment.yaml` | Game service deployment with replicas |
| `service.yaml` | ClusterIP service for internal communication |
| `ingress.yaml` | Ingress with TLS termination |
| `hpa.yaml` | Horizontal Pod Autoscaler |
| `pdb.yaml` | Pod Disruption Budget for HA |

#### 1.3 Database StatefulSets
**Directory: `k8s/base/databases/`**

| Component | Configuration |
|-----------|---------------|
| PostgreSQL | StatefulSet with PVC, 3 replicas (primary + 2 read replicas) |
| Cassandra | StatefulSet with 3 nodes, rack-aware deployment |
| Redis | Redis Sentinel with 3 nodes for HA |

#### 1.4 Load Balancer Configuration
- NGINX Ingress Controller
- Session affinity (sticky sessions) for WebSocket support
- Rate limiting at ingress level
- SSL/TLS termination with cert-manager

---

### Phase 2: Monitoring & Observability Stack

#### 2.1 Prometheus Setup
**Directory: `k8s/monitoring/prometheus/`**

| File | Purpose |
|------|---------|
| `prometheus-config.yaml` | Scrape configs for all services |
| `prometheus-deployment.yaml` | Prometheus server deployment |
| `prometheus-rules.yaml` | Recording rules for performance metrics |
| `servicemonitor.yaml` | ServiceMonitor CRDs for auto-discovery |

**Key Metrics to Collect:**
- HTTP request latency (p50, p95, p99)
- Request throughput (requests/sec)
- Error rates (4xx, 5xx)
- Database connection pool utilization
- Redis operations latency
- Cassandra write latency
- JVM heap usage
- Game-specific metrics (answers/sec, active games, active players)

#### 2.2 Grafana Dashboards
**Directory: `k8s/monitoring/grafana/`**

| Dashboard | Panels |
|-----------|--------|
| **Application Overview** | Request rate, latency histogram, error rate, active connections |
| **Database Health** | PostgreSQL connections, query latency, Cassandra writes/sec, Redis hit rate |
| **Game Metrics** | Active games, players online, answers submitted, leaderboard updates |
| **Infrastructure** | CPU, memory, disk I/O, network traffic per pod |
| **Business KPIs** | Games completed, total rewards distributed, user engagement |

#### 2.3 Distributed Tracing (Jaeger/Zipkin)
**Directory: `k8s/monitoring/tracing/`**

- Jaeger deployment with Elasticsearch backend
- Spring Cloud Sleuth integration
- Trace sampling configuration (10% in production)
- Cross-service correlation IDs

#### 2.4 Log Aggregation (EFK Stack)
**Directory: `k8s/monitoring/logging/`**

| Component | Purpose |
|-----------|---------|
| Fluent Bit | Lightweight log collector as DaemonSet |
| Elasticsearch | Log storage and indexing |
| Kibana | Log visualization and search |

---

### Phase 3: Alerting System

#### 3.1 Alertmanager Configuration
**File: `k8s/monitoring/alertmanager/alertmanager-config.yaml`**

**Alert Routes:**
- Critical alerts → PagerDuty + Slack #critical
- Warning alerts → Slack #alerts
- Info alerts → Slack #monitoring

#### 3.2 Alert Rules
**File: `k8s/monitoring/prometheus/alert-rules.yaml`**

| Alert | Condition | Severity |
|-------|-----------|----------|
| HighErrorRate | Error rate > 5% for 5 minutes | Critical |
| HighLatency | P99 latency > 500ms for 5 minutes | Warning |
| DatabaseConnectionPoolExhausted | Pool usage > 90% for 2 minutes | Critical |
| PodCrashLooping | Restarts > 3 in 10 minutes | Critical |
| HighMemoryUsage | Memory > 85% for 5 minutes | Warning |
| GameStuckInActiveState | Game active > 2 hours | Warning |
| CassandraWriteLatencyHigh | Write latency > 100ms for 5 minutes | Warning |
| RedisMemoryHigh | Memory > 80% for 5 minutes | Warning |
| NoActiveGames | 0 active games during peak hours | Info |
| BudgetOverspend | Rewards > budget | Critical |

#### 3.3 Integration Endpoints
- PagerDuty for on-call escalation
- Slack webhooks for team notifications
- Email for daily summaries
- OpsGenie as backup

---

### Phase 4: Game Scheduling & Automation

#### 4.1 Game Scheduler Service
**New Module: `game-scheduler/`**

A dedicated microservice for game lifecycle automation:

```
game-scheduler/
├── src/main/kotlin/
│   └── com/gameplatform/scheduler/
│       ├── SchedulerApplication.kt
│       ├── service/
│       │   ├── GameSchedulerService.kt      # Core scheduling logic
│       │   ├── GameLifecycleOrchestrator.kt # Start/end coordination
│       │   └── NotificationService.kt       # Player notifications
│       ├── repository/
│       │   └── ScheduledGameRepository.kt
│       ├── job/
│       │   ├── GameStartJob.kt              # Quartz job for game start
│       │   ├── GameEndJob.kt                # Quartz job for game end
│       │   └── QuestionActivationJob.kt     # Auto-activate questions
│       └── config/
│           └── QuartzConfig.kt
```

#### 4.2 Scheduling Features

| Feature | Description |
|---------|-------------|
| **Scheduled Game Creation** | Define games to start at specific times |
| **Auto-Start** | Automatically transition SCHEDULED → ACTIVE at start time |
| **Auto-End** | Complete games after all questions answered or time limit |
| **Question Pacing** | Automatically activate next question after timer expires |
| **Player Notifications** | Send reminders before game starts (5 min, 1 min) |
| **Recurring Games** | Daily/weekly recurring game templates |

#### 4.3 Database Schema Extension
**File: `infrastructure/src/main/resources/db/migration/V006__scheduled_games.sql`**

```sql
CREATE TABLE scheduled_games (
    id UUID PRIMARY KEY,
    game_id UUID REFERENCES games(id),
    scheduled_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end_time TIMESTAMP WITH TIME ZONE,
    recurrence_rule VARCHAR(255),  -- CRON expression
    notification_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE game_schedule_history (
    id UUID PRIMARY KEY,
    scheduled_game_id UUID REFERENCES scheduled_games(id),
    action VARCHAR(50) NOT NULL,  -- STARTED, ENDED, CANCELLED
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    result VARCHAR(50),
    error_message TEXT
);
```

#### 4.4 Kubernetes CronJobs (Alternative for simpler setups)
**File: `k8s/jobs/game-scheduler-cronjob.yaml`**

For simpler deployments, use Kubernetes CronJobs to trigger game lifecycle events.

---

### Phase 5: Real-Time Features (WebSocket)

#### 5.1 WebSocket Gateway
**New Package: `game-service/src/main/kotlin/.../websocket/`**

| Class | Purpose |
|-------|---------|
| `WebSocketConfig.kt` | STOMP over WebSocket configuration |
| `GameWebSocketHandler.kt` | Connection management, authentication |
| `GameEventPublisher.kt` | Publish events to subscribers |
| `PlayerSessionRegistry.kt` | Track active player connections |

#### 5.2 Event Types

| Event | Payload | When Sent |
|-------|---------|-----------|
| `game.starting` | Game ID, countdown | 60 seconds before start |
| `question.activated` | Question data, timer | When question becomes active |
| `answer.received` | User ID, timestamp | Confirmation to answerer |
| `question.winner` | Winner info, reward | When winner determined |
| `question.expired` | Question ID | When timer expires |
| `leaderboard.update` | Top 10 players | After each answer |
| `game.completed` | Final results | When game ends |

#### 5.3 Redis Pub/Sub for Multi-Pod
Use Redis Pub/Sub to broadcast WebSocket events across all pods, ensuring all connected clients receive updates regardless of which pod they're connected to.

---

### Phase 6: Security Enhancements

#### 6.1 Authentication & Authorization
**Package: `game-service/src/main/kotlin/.../security/`**

| Component | Implementation |
|-----------|----------------|
| JWT Authentication | Spring Security with JWT tokens |
| OAuth2 Support | Google, Facebook, Apple login |
| Role-Based Access | ADMIN, HOST, PLAYER roles |
| API Key Auth | For server-to-server communication |

#### 6.2 Rate Limiting
**File: `game-service/src/main/kotlin/.../filter/RateLimitFilter.kt`**

| Endpoint | Limit |
|----------|-------|
| `/api/answers` | 10 requests/second per user |
| `/api/games` (create) | 5 requests/minute per user |
| `/api/games` (read) | 100 requests/minute per user |
| Global | 10,000 requests/minute per IP |

Implementation using Redis sliding window algorithm.

#### 6.3 Security Headers & CORS
- Strict CSP headers
- CORS configuration for allowed origins
- HSTS enforcement
- XSS protection headers

---

### Phase 7: CI/CD Pipeline

#### 7.1 GitHub Actions Workflow
**File: `.github/workflows/ci-cd.yaml`**

```yaml
Stages:
1. Build & Test
   - Compile Kotlin code
   - Run unit tests
   - Run integration tests
   - Code coverage report

2. Security Scan
   - Dependency vulnerability scan (Snyk/Dependabot)
   - Container image scan (Trivy)
   - SAST analysis (SonarQube)

3. Build & Push Image
   - Build Docker image
   - Tag with commit SHA and version
   - Push to container registry (ECR/GCR/DockerHub)

4. Deploy to Staging
   - Apply Kubernetes manifests to staging
   - Run smoke tests
   - Run E2E tests

5. Deploy to Production
   - Manual approval gate
   - Blue-green or canary deployment
   - Post-deployment health checks
```

#### 7.2 Environment Configurations
**Directory: `k8s/overlays/`**

| Environment | Configuration |
|-------------|---------------|
| `dev/` | Single replica, debug logging, mock services |
| `staging/` | Production-like, reduced resources |
| `production/` | Full HA, optimized resources, strict security |

---

### Phase 8: Additional Production Features

#### 8.1 Backup & Disaster Recovery

| Component | Strategy |
|-----------|----------|
| PostgreSQL | Daily automated backups to S3, point-in-time recovery |
| Cassandra | Snapshot backups, cross-region replication |
| Redis | RDB snapshots every 15 minutes, AOF persistence |

#### 8.2 Database Migrations Strategy
- Flyway for PostgreSQL schema versioning
- Blue-green deployment for zero-downtime migrations
- Rollback procedures documented

#### 8.3 Feature Flags
Implement feature flag system for:
- Gradual rollout of new game types
- A/B testing of UI features
- Emergency kill switches

#### 8.4 Admin Dashboard
**New Module: `admin-dashboard/`**

React-based admin interface for:
- Game management (create, schedule, cancel)
- User management
- Real-time metrics visualization
- Manual intervention tools
- Audit logs

---

## File Structure Overview

```
game-engine/
├── Dockerfile                          # Application container
├── docker-compose.yml                  # Local development
├── docker-compose.prod.yml             # Production-like local
├── k8s/
│   ├── base/
│   │   ├── namespace.yaml
│   │   ├── configmap.yaml
│   │   ├── secret.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── ingress.yaml
│   │   ├── hpa.yaml
│   │   ├── pdb.yaml
│   │   └── databases/
│   │       ├── postgresql/
│   │       │   ├── statefulset.yaml
│   │       │   ├── service.yaml
│   │       │   └── pvc.yaml
│   │       ├── cassandra/
│   │       │   ├── statefulset.yaml
│   │       │   ├── service.yaml
│   │       │   └── pvc.yaml
│   │       └── redis/
│   │           ├── statefulset.yaml
│   │           ├── service.yaml
│   │           └── sentinel.yaml
│   ├── monitoring/
│   │   ├── prometheus/
│   │   │   ├── deployment.yaml
│   │   │   ├── config.yaml
│   │   │   ├── rules.yaml
│   │   │   └── servicemonitor.yaml
│   │   ├── grafana/
│   │   │   ├── deployment.yaml
│   │   │   ├── datasources.yaml
│   │   │   └── dashboards/
│   │   │       ├── application.json
│   │   │       ├── database.json
│   │   │       ├── game-metrics.json
│   │   │       └── infrastructure.json
│   │   ├── alertmanager/
│   │   │   ├── deployment.yaml
│   │   │   └── config.yaml
│   │   ├── tracing/
│   │   │   └── jaeger.yaml
│   │   └── logging/
│   │       ├── fluent-bit.yaml
│   │       ├── elasticsearch.yaml
│   │       └── kibana.yaml
│   ├── jobs/
│   │   └── game-scheduler-cronjob.yaml
│   └── overlays/
│       ├── dev/
│       │   └── kustomization.yaml
│       ├── staging/
│       │   └── kustomization.yaml
│       └── production/
│           └── kustomization.yaml
├── game-scheduler/                     # New scheduling module
│   ├── build.gradle.kts
│   └── src/
├── admin-dashboard/                    # New admin UI
│   ├── package.json
│   └── src/
├── .github/
│   └── workflows/
│       ├── ci.yaml
│       └── cd.yaml
└── scripts/
    ├── deploy.sh
    ├── backup.sh
    └── rollback.sh
```

---

## Priority Implementation Order

### Tier 1: Critical (Must Have for Production)
1. Dockerfile for application containerization
2. Kubernetes deployment manifests (base)
3. Horizontal Pod Autoscaler
4. Prometheus + Grafana monitoring stack
5. Critical alerts (error rate, latency, pod health)
6. CI/CD pipeline (build, test, deploy)

### Tier 2: Important (Production Readiness)
7. Game scheduler service (auto-start/end)
8. Authentication (JWT)
9. Rate limiting
10. Load balancer with ingress
11. Database HA (PostgreSQL replicas, Redis Sentinel)
12. Log aggregation (EFK stack)

### Tier 3: Enhanced (Production Excellence)
13. WebSocket real-time updates
14. Distributed tracing (Jaeger)
15. Advanced Grafana dashboards
16. PagerDuty/Slack alerting integration
17. Feature flags
18. Admin dashboard

### Tier 4: Future Enhancements
19. OAuth2 social login
20. MCQ_FASTEST game type
21. Cross-region disaster recovery
22. A/B testing framework
23. Mobile push notifications

---

## Resource Estimates

### Kubernetes Cluster Requirements (Production)

| Component | Replicas | CPU (request/limit) | Memory (request/limit) |
|-----------|----------|---------------------|------------------------|
| Game Service | 3-10 (HPA) | 500m/2 | 1Gi/2Gi |
| Game Scheduler | 2 | 250m/500m | 512Mi/1Gi |
| PostgreSQL Primary | 1 | 1/2 | 2Gi/4Gi |
| PostgreSQL Replicas | 2 | 500m/1 | 1Gi/2Gi |
| Cassandra | 3 | 2/4 | 4Gi/8Gi |
| Redis Sentinel | 3 | 250m/500m | 512Mi/1Gi |
| Prometheus | 1 | 500m/1 | 2Gi/4Gi |
| Grafana | 1 | 250m/500m | 512Mi/1Gi |
| Alertmanager | 1 | 100m/200m | 256Mi/512Mi |
| Jaeger | 1 | 500m/1 | 1Gi/2Gi |

**Total Cluster:** Minimum 3 nodes (8 CPU, 32GB RAM each)

---

## Success Metrics

After implementation, the platform should achieve:

| Metric | Target |
|--------|--------|
| Availability | 99.9% uptime |
| Response Time | P99 < 200ms |
| Error Rate | < 0.1% |
| Deployment Frequency | Multiple per day |
| MTTR (Mean Time to Recovery) | < 15 minutes |
| Concurrent Users | 10,000+ |
| Alert Response Time | < 5 minutes for critical |

---

## Next Steps

1. Review and approve this plan
2. Create implementation tickets/issues
3. Begin Phase 1 implementation
4. Iterate and refine based on learnings

---

*Document Version: 1.0*
*Created: 2026-01-15*
*Author: Claude Code*

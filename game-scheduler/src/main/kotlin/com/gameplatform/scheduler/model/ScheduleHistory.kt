package com.gameplatform.scheduler.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "schedule_history")
data class ScheduleHistory(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "scheduled_game_id", nullable = false)
    val scheduledGameId: UUID,

    @Column(name = "game_id", nullable = false)
    val gameId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    val action: ScheduleAction,

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    val result: ActionResult,

    @Column(name = "error_message")
    val errorMessage: String? = null,

    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant = Instant.now()
)

enum class ScheduleAction {
    GAME_STARTED,
    GAME_ENDED,
    QUESTION_ACTIVATED,
    NOTIFICATION_SENT,
    SCHEDULE_CREATED,
    SCHEDULE_CANCELLED
}

enum class ActionResult {
    SUCCESS,
    FAILURE
}
package com.gameplatform.scheduler.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "scheduled_games")
data class ScheduledGame(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "game_id", nullable = false)
    val gameId: UUID,

    @Column(name = "scheduled_start_time", nullable = false)
    val scheduledStartTime: Instant,

    @Column(name = "scheduled_end_time")
    val scheduledEndTime: Instant? = null,

    @Column(name = "auto_activate_questions")
    val autoActivateQuestions: Boolean = true,

    @Column(name = "question_interval_seconds")
    val questionIntervalSeconds: Int = 30,

    @Column(name = "recurrence_rule")
    val recurrenceRule: String? = null,  // CRON expression for recurring games

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ScheduleStatus = ScheduleStatus.PENDING,

    @Column(name = "last_notification_sent")
    var lastNotificationSent: Instant? = null,

    @Column(name = "job_id")
    var jobId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

enum class ScheduleStatus {
    PENDING,      // Waiting to be scheduled
    SCHEDULED,    // Job created in Quartz
    STARTED,      // Game has been started
    COMPLETED,    // Game finished
    CANCELLED,    // Schedule was cancelled
    FAILED        // Failed to start
}
package com.gameplatform.scheduler.repository

import com.gameplatform.scheduler.model.ScheduleStatus
import com.gameplatform.scheduler.model.ScheduledGame
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ScheduledGameRepository : JpaRepository<ScheduledGame, UUID> {

    fun findByGameId(gameId: UUID): ScheduledGame?

    fun findByStatus(status: ScheduleStatus): List<ScheduledGame>

    @Query("""
        SELECT sg FROM ScheduledGame sg
        WHERE sg.status = :status
        AND sg.scheduledStartTime BETWEEN :from AND :to
        ORDER BY sg.scheduledStartTime ASC
    """)
    fun findPendingGamesInTimeRange(
        status: ScheduleStatus = ScheduleStatus.PENDING,
        from: Instant,
        to: Instant
    ): List<ScheduledGame>

    @Query("""
        SELECT sg FROM ScheduledGame sg
        WHERE sg.status = 'SCHEDULED'
        AND sg.scheduledStartTime <= :now
        ORDER BY sg.scheduledStartTime ASC
    """)
    fun findGamesToStart(now: Instant): List<ScheduledGame>

    @Query("""
        SELECT sg FROM ScheduledGame sg
        WHERE sg.status = 'STARTED'
        AND sg.scheduledEndTime IS NOT NULL
        AND sg.scheduledEndTime <= :now
    """)
    fun findGamesToEnd(now: Instant): List<ScheduledGame>

    fun findByStatusIn(statuses: List<ScheduleStatus>): List<ScheduledGame>
}

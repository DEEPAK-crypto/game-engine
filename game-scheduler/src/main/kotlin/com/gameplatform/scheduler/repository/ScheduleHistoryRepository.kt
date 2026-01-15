package com.gameplatform.scheduler.repository

import com.gameplatform.scheduler.model.ScheduleHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ScheduleHistoryRepository : JpaRepository<ScheduleHistory, UUID> {

    fun findByScheduledGameIdOrderByExecutedAtDesc(scheduledGameId: UUID): List<ScheduleHistory>

    fun findByGameIdOrderByExecutedAtDesc(gameId: UUID): List<ScheduleHistory>
}

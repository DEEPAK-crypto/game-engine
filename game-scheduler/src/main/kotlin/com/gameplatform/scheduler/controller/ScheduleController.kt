package com.gameplatform.scheduler.controller

import com.gameplatform.scheduler.model.ScheduleHistory
import com.gameplatform.scheduler.model.ScheduleStatus
import com.gameplatform.scheduler.model.ScheduledGame
import com.gameplatform.scheduler.service.ScheduleGameRequest
import com.gameplatform.scheduler.service.SchedulerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/schedules")
class ScheduleController(
    private val schedulerService: SchedulerService
) {

    @PostMapping
    fun scheduleGame(@RequestBody request: CreateScheduleRequest): ResponseEntity<ScheduledGameResponse> {
        val scheduledGame = schedulerService.scheduleGame(
            ScheduleGameRequest(
                gameId = request.gameId,
                scheduledStartTime = request.scheduledStartTime,
                scheduledEndTime = request.scheduledEndTime,
                autoActivateQuestions = request.autoActivateQuestions,
                questionIntervalSeconds = request.questionIntervalSeconds,
                recurrenceRule = request.recurrenceRule
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduledGame.toResponse())
    }

    @GetMapping("/{id}")
    fun getSchedule(@PathVariable id: UUID): ResponseEntity<ScheduledGameResponse> {
        val scheduledGame = schedulerService.getScheduledGame(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(scheduledGame.toResponse())
    }

    @GetMapping("/by-game/{gameId}")
    fun getScheduleByGameId(@PathVariable gameId: UUID): ResponseEntity<ScheduledGameResponse> {
        val scheduledGame = schedulerService.getScheduledGameByGameId(gameId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(scheduledGame.toResponse())
    }

    @GetMapping
    fun listSchedules(
        @RequestParam(required = false) status: List<ScheduleStatus>?
    ): ResponseEntity<List<ScheduledGameResponse>> {
        val schedules = schedulerService.listScheduledGames(status)
        return ResponseEntity.ok(schedules.map { it.toResponse() })
    }

    @DeleteMapping("/{id}")
    fun cancelSchedule(@PathVariable id: UUID): ResponseEntity<ScheduledGameResponse> {
        val scheduledGame = schedulerService.cancelSchedule(id)
        return ResponseEntity.ok(scheduledGame.toResponse())
    }

    @GetMapping("/{id}/history")
    fun getScheduleHistory(@PathVariable id: UUID): ResponseEntity<List<ScheduleHistoryResponse>> {
        val history = schedulerService.getHistory(id)
        return ResponseEntity.ok(history.map { it.toResponse() })
    }

    private fun ScheduledGame.toResponse() = ScheduledGameResponse(
        id = id,
        gameId = gameId,
        scheduledStartTime = scheduledStartTime,
        scheduledEndTime = scheduledEndTime,
        autoActivateQuestions = autoActivateQuestions,
        questionIntervalSeconds = questionIntervalSeconds,
        recurrenceRule = recurrenceRule,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ScheduleHistory.toResponse() = ScheduleHistoryResponse(
        id = id,
        scheduledGameId = scheduledGameId,
        gameId = gameId,
        action = action.name,
        result = result.name,
        errorMessage = errorMessage,
        executedAt = executedAt
    )
}

data class CreateScheduleRequest(
    val gameId: UUID,
    val scheduledStartTime: Instant,
    val scheduledEndTime: Instant? = null,
    val autoActivateQuestions: Boolean = true,
    val questionIntervalSeconds: Int = 30,
    val recurrenceRule: String? = null
)

data class ScheduledGameResponse(
    val id: UUID,
    val gameId: UUID,
    val scheduledStartTime: Instant,
    val scheduledEndTime: Instant?,
    val autoActivateQuestions: Boolean,
    val questionIntervalSeconds: Int,
    val recurrenceRule: String?,
    val status: ScheduleStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ScheduleHistoryResponse(
    val id: UUID,
    val scheduledGameId: UUID,
    val gameId: UUID,
    val action: String,
    val result: String,
    val errorMessage: String?,
    val executedAt: Instant
)
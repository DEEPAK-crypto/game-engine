package com.gameplatform.scheduler.service

import com.gameplatform.scheduler.job.GameEndJob
import com.gameplatform.scheduler.job.GameStartJob
import com.gameplatform.scheduler.model.*
import com.gameplatform.scheduler.repository.ScheduleHistoryRepository
import com.gameplatform.scheduler.repository.ScheduledGameRepository
import org.quartz.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class SchedulerService(
    private val scheduledGameRepository: ScheduledGameRepository,
    private val historyRepository: ScheduleHistoryRepository,
    private val schedulerFactoryBean: SchedulerFactoryBean
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private val scheduler: Scheduler by lazy { schedulerFactoryBean.scheduler }

    @Transactional
    fun scheduleGame(request: ScheduleGameRequest): ScheduledGame {
        logger.info("Scheduling game ${request.gameId} to start at ${request.scheduledStartTime}")

        // Create scheduled game entity
        val scheduledGame = ScheduledGame(
            gameId = request.gameId,
            scheduledStartTime = request.scheduledStartTime,
            scheduledEndTime = request.scheduledEndTime,
            autoActivateQuestions = request.autoActivateQuestions,
            questionIntervalSeconds = request.questionIntervalSeconds,
            recurrenceRule = request.recurrenceRule
        )

        val saved = scheduledGameRepository.save(scheduledGame)

        // Create Quartz job for game start
        scheduleStartJob(saved)

        // If end time is specified, create Quartz job for game end
        if (saved.scheduledEndTime != null) {
            scheduleEndJob(saved)
        }

        // Update status
        saved.status = ScheduleStatus.SCHEDULED
        saved.updatedAt = Instant.now()
        val result = scheduledGameRepository.save(saved)

        // Record history
        recordHistory(result.id, result.gameId, ScheduleAction.SCHEDULE_CREATED, ActionResult.SUCCESS)

        logger.info("Game ${request.gameId} scheduled with id ${result.id}")
        return result
    }

    @Transactional
    fun cancelSchedule(scheduledGameId: UUID): ScheduledGame {
        val scheduledGame = scheduledGameRepository.findById(scheduledGameId)
            .orElseThrow { IllegalArgumentException("ScheduledGame not found: $scheduledGameId") }

        if (scheduledGame.status == ScheduleStatus.STARTED || scheduledGame.status == ScheduleStatus.COMPLETED) {
            throw IllegalStateException("Cannot cancel a game that has already started or completed")
        }

        // Remove Quartz jobs
        val startJobKey = JobKey.jobKey("start-${scheduledGame.id}", GameStartJob.JOB_GROUP)
        val endJobKey = JobKey.jobKey("end-${scheduledGame.id}", GameEndJob.JOB_GROUP)

        scheduler.deleteJob(startJobKey)
        scheduler.deleteJob(endJobKey)

        // Update status
        scheduledGame.status = ScheduleStatus.CANCELLED
        scheduledGame.updatedAt = Instant.now()
        val result = scheduledGameRepository.save(scheduledGame)

        // Record history
        recordHistory(result.id, result.gameId, ScheduleAction.SCHEDULE_CANCELLED, ActionResult.SUCCESS)

        logger.info("Schedule ${scheduledGameId} cancelled")
        return result
    }

    fun getScheduledGame(id: UUID): ScheduledGame? {
        return scheduledGameRepository.findById(id).orElse(null)
    }

    fun getScheduledGameByGameId(gameId: UUID): ScheduledGame? {
        return scheduledGameRepository.findByGameId(gameId)
    }

    fun listScheduledGames(statuses: List<ScheduleStatus>? = null): List<ScheduledGame> {
        return if (statuses.isNullOrEmpty()) {
            scheduledGameRepository.findAll()
        } else {
            scheduledGameRepository.findByStatusIn(statuses)
        }
    }

    fun getHistory(scheduledGameId: UUID): List<ScheduleHistory> {
        return historyRepository.findByScheduledGameIdOrderByExecutedAtDesc(scheduledGameId)
    }

    private fun scheduleStartJob(scheduledGame: ScheduledGame) {
        val jobDetail = JobBuilder.newJob(GameStartJob::class.java)
            .withIdentity("start-${scheduledGame.id}", GameStartJob.JOB_GROUP)
            .usingJobData("scheduledGameId", scheduledGame.id.toString())
            .usingJobData("gameId", scheduledGame.gameId.toString())
            .storeDurably(false)
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("start-trigger-${scheduledGame.id}", GameStartJob.JOB_GROUP)
            .startAt(Date.from(scheduledGame.scheduledStartTime))
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withMisfireHandlingInstructionFireNow())
            .build()

        scheduler.scheduleJob(jobDetail, trigger)
        logger.info("Scheduled start job for game ${scheduledGame.gameId} at ${scheduledGame.scheduledStartTime}")
    }

    private fun scheduleEndJob(scheduledGame: ScheduledGame) {
        val endTime = scheduledGame.scheduledEndTime ?: return

        val jobDetail = JobBuilder.newJob(GameEndJob::class.java)
            .withIdentity("end-${scheduledGame.id}", GameEndJob.JOB_GROUP)
            .usingJobData("scheduledGameId", scheduledGame.id.toString())
            .usingJobData("gameId", scheduledGame.gameId.toString())
            .storeDurably(false)
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("end-trigger-${scheduledGame.id}", GameEndJob.JOB_GROUP)
            .startAt(Date.from(endTime))
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withMisfireHandlingInstructionFireNow())
            .build()

        scheduler.scheduleJob(jobDetail, trigger)
        logger.info("Scheduled end job for game ${scheduledGame.gameId} at $endTime")
    }

    private fun recordHistory(
        scheduledGameId: UUID,
        gameId: UUID,
        action: ScheduleAction,
        result: ActionResult,
        errorMessage: String? = null
    ) {
        val history = ScheduleHistory(
            scheduledGameId = scheduledGameId,
            gameId = gameId,
            action = action,
            result = result,
            errorMessage = errorMessage
        )
        historyRepository.save(history)
    }
}

data class ScheduleGameRequest(
    val gameId: UUID,
    val scheduledStartTime: Instant,
    val scheduledEndTime: Instant? = null,
    val autoActivateQuestions: Boolean = true,
    val questionIntervalSeconds: Int = 30,
    val recurrenceRule: String? = null
)

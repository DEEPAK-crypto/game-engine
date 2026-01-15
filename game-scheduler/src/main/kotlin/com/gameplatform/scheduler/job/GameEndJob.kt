package com.gameplatform.scheduler.job

import com.gameplatform.scheduler.service.GameLifecycleService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GameEndJob(
    private val gameLifecycleService: GameLifecycleService
) : Job {

    private val logger = LoggerFactory.getLogger(GameEndJob::class.java)

    override fun execute(context: JobExecutionContext) {
        val jobDataMap = context.mergedJobDataMap
        val scheduledGameId = UUID.fromString(jobDataMap.getString("scheduledGameId"))
        val gameId = UUID.fromString(jobDataMap.getString("gameId"))

        logger.info("Executing GameEndJob for scheduledGameId=$scheduledGameId, gameId=$gameId")

        try {
            gameLifecycleService.endGame(scheduledGameId, gameId)
            logger.info("Successfully ended game $gameId")
        } catch (e: Exception) {
            logger.error("Failed to end game $gameId: ${e.message}", e)
            throw e
        }
    }

    companion object {
        const val JOB_GROUP = "game-end-jobs"
    }
}
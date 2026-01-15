package com.gameplatform.scheduler.job

import com.gameplatform.scheduler.service.GameLifecycleService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GameStartJob(
    private val gameLifecycleService: GameLifecycleService
) : Job {

    private val logger = LoggerFactory.getLogger(GameStartJob::class.java)

    override fun execute(context: JobExecutionContext) {
        val jobDataMap = context.mergedJobDataMap
        val scheduledGameId = UUID.fromString(jobDataMap.getString("scheduledGameId"))
        val gameId = UUID.fromString(jobDataMap.getString("gameId"))

        logger.info("Executing GameStartJob for scheduledGameId=$scheduledGameId, gameId=$gameId")

        try {
            gameLifecycleService.startGame(scheduledGameId, gameId)
            logger.info("Successfully started game $gameId")
        } catch (e: Exception) {
            logger.error("Failed to start game $gameId: ${e.message}", e)
            throw e
        }
    }

    companion object {
        const val JOB_GROUP = "game-start-jobs"
    }
}
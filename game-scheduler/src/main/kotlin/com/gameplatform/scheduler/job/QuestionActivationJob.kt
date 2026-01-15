package com.gameplatform.scheduler.job

import com.gameplatform.scheduler.service.GameLifecycleService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionActivationJob(
    private val gameLifecycleService: GameLifecycleService
) : Job {

    private val logger = LoggerFactory.getLogger(QuestionActivationJob::class.java)

    override fun execute(context: JobExecutionContext) {
        val jobDataMap = context.mergedJobDataMap
        val gameId = UUID.fromString(jobDataMap.getString("gameId"))
        val questionId = UUID.fromString(jobDataMap.getString("questionId"))

        logger.info("Executing QuestionActivationJob for gameId=$gameId, questionId=$questionId")

        try {
            gameLifecycleService.activateQuestion(gameId, questionId)
            logger.info("Successfully activated question $questionId for game $gameId")
        } catch (e: Exception) {
            logger.error("Failed to activate question $questionId: ${e.message}", e)
            throw e
        }
    }

    companion object {
        const val JOB_GROUP = "question-activation-jobs"
    }
}
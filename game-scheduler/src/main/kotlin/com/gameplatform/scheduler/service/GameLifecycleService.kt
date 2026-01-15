package com.gameplatform.scheduler.service

import com.gameplatform.scheduler.model.*
import com.gameplatform.scheduler.repository.ScheduleHistoryRepository
import com.gameplatform.scheduler.repository.ScheduledGameRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

@Service
class GameLifecycleService(
    private val scheduledGameRepository: ScheduledGameRepository,
    private val historyRepository: ScheduleHistoryRepository,
    private val restTemplate: RestTemplate,
    @Value("\${game-service.url}") private val gameServiceUrl: String
) {
    private val logger = LoggerFactory.getLogger(GameLifecycleService::class.java)

    @Transactional
    fun startGame(scheduledGameId: UUID, gameId: UUID) {
        val scheduledGame = scheduledGameRepository.findById(scheduledGameId)
            .orElseThrow { IllegalStateException("ScheduledGame not found: $scheduledGameId") }

        try {
            // Call game-service to start the game
            val url = "$gameServiceUrl/api/games/$gameId/start"
            restTemplate.postForEntity(url, null, Void::class.java)

            // Update status
            scheduledGame.status = ScheduleStatus.STARTED
            scheduledGame.updatedAt = Instant.now()
            scheduledGameRepository.save(scheduledGame)

            // Record history
            recordHistory(scheduledGameId, gameId, ScheduleAction.GAME_STARTED, ActionResult.SUCCESS)

            logger.info("Game $gameId started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start game $gameId: ${e.message}", e)

            scheduledGame.status = ScheduleStatus.FAILED
            scheduledGame.updatedAt = Instant.now()
            scheduledGameRepository.save(scheduledGame)

            recordHistory(scheduledGameId, gameId, ScheduleAction.GAME_STARTED, ActionResult.FAILURE, e.message)
            throw e
        }
    }

    @Transactional
    fun endGame(scheduledGameId: UUID, gameId: UUID) {
        val scheduledGame = scheduledGameRepository.findById(scheduledGameId)
            .orElseThrow { IllegalStateException("ScheduledGame not found: $scheduledGameId") }

        try {
            // Call game-service to complete the game
            val url = "$gameServiceUrl/api/games/$gameId/complete"
            restTemplate.postForEntity(url, null, Void::class.java)

            // Update status
            scheduledGame.status = ScheduleStatus.COMPLETED
            scheduledGame.updatedAt = Instant.now()
            scheduledGameRepository.save(scheduledGame)

            // Record history
            recordHistory(scheduledGameId, gameId, ScheduleAction.GAME_ENDED, ActionResult.SUCCESS)

            logger.info("Game $gameId ended successfully")
        } catch (e: Exception) {
            logger.error("Failed to end game $gameId: ${e.message}", e)
            recordHistory(scheduledGameId, gameId, ScheduleAction.GAME_ENDED, ActionResult.FAILURE, e.message)
            throw e
        }
    }

    fun activateQuestion(gameId: UUID, questionId: UUID) {
        try {
            val url = "$gameServiceUrl/api/games/$gameId/questions/$questionId/activate"
            restTemplate.postForEntity(url, null, Void::class.java)

            logger.info("Question $questionId activated for game $gameId")
        } catch (e: Exception) {
            logger.error("Failed to activate question $questionId: ${e.message}", e)
            throw e
        }
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

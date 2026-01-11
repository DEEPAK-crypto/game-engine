package com.gameplatform.game.service.impl

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.dto.GameResponse
import com.gameplatform.game.exception.GameAlreadyCompletedException
import com.gameplatform.game.exception.GameAlreadyStartedException
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InvalidGameStateException
import com.gameplatform.game.metrics.GameMetrics
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.GameService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class GameServiceImpl(
    private val gameRepository: GameRepository,
    private val gameMetrics: GameMetrics,
    private val activeQuestionCacheService: com.gameplatform.game.service.ActiveQuestionCacheService
) : GameService {

    private val log = LoggerFactory.getLogger(GameServiceImpl::class.java)

    override fun createGame(request: CreateGameRequest): GameResponse {
        log.info(
            "Creating new game",
            kv("name", request.name),
            kv("gameType", request.gameType),
            kv("initialBudget", request.initialBudget),
            kv("scheduledAt", request.scheduledAt)
        )

        val now = Instant.now()
        val status = if (request.scheduledAt != null && request.scheduledAt > now) {
            GameStatus.SCHEDULED
        } else {
            GameStatus.DRAFT
        }

        val game = Game(
            id = UUID.randomUUID(),
            name = request.name,
            gameType = request.gameType,
            initialBudget = request.initialBudget,
            remainingBudget = request.initialBudget,
            status = status,
            scheduledAt = request.scheduledAt,
            startedAt = null,
            endedAt = null,
            questionTimerSeconds = request.questionTimerSeconds,
            createdAt = now,
            updatedAt = now
        )

        val saved = gameRepository.save(game)

        log.info(
            "Game created successfully",
            kv("gameId", saved.id),
            kv("name", saved.name),
            kv("status", saved.status),
            kv("initialBudget", saved.initialBudget)
        )

        gameMetrics.recordGameCreated(saved.id)

        return GameResponse.from(saved)
    }

    @Transactional(readOnly = true)
    override fun getGame(gameId: UUID): GameResponse {
        log.debug("Retrieving game", kv("gameId", gameId))
        val game = gameRepository.findById(gameId)
            ?: run {
                log.warn("Game not found", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }
        return GameResponse.from(game)
    }

    @Transactional(readOnly = true)
    override fun getAllGames(): List<GameResponse> {
        return gameRepository.findAll().map { GameResponse.from(it) }
    }

    @Transactional(readOnly = true)
    override fun getGamesByStatus(status: GameStatus): List<GameResponse> {
        return gameRepository.findByStatus(status).map { GameResponse.from(it) }
    }

    override fun startGame(gameId: UUID, startAt: Instant): GameResponse {
        log.info(
            "Starting game",
            kv("gameId", gameId),
            kv("startAt", startAt)
        )

        // First, get the game to check if it exists and get current status for error messages
        val game = gameRepository.findById(gameId)
            ?: run {
                log.warn("Game not found when starting", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }

        // Try atomic transition from DRAFT -> ACTIVE
        var success = gameRepository.transitionStatus(
            gameId,
            expectedStatus = GameStatus.DRAFT,
            newStatus = GameStatus.ACTIVE,
            timestamp = startAt
        )

        // If DRAFT transition failed, try SCHEDULED -> ACTIVE
        if (!success) {
            success = gameRepository.transitionStatus(
                gameId,
                expectedStatus = GameStatus.SCHEDULED,
                newStatus = GameStatus.ACTIVE,
                timestamp = startAt
            )
        }

        // If both transitions failed, the game was in an invalid state
        if (!success) {
            // Re-fetch to get current status for accurate error message
            val currentGame = gameRepository.findById(gameId)
                ?: throw GameNotFoundException(gameId)

            when (currentGame.status) {
                GameStatus.ACTIVE -> {
                    log.warn(
                        "Attempt to start already active game (concurrent start detected)",
                        kv("gameId", gameId),
                        kv("status", currentGame.status)
                    )
                    throw GameAlreadyStartedException(gameId)
                }
                GameStatus.COMPLETED -> {
                    log.warn(
                        "Attempt to start completed game",
                        kv("gameId", gameId),
                        kv("status", currentGame.status)
                    )
                    throw GameAlreadyCompletedException(gameId)
                }
                else -> {
                    log.error(
                        "Failed to start game - unexpected state",
                        kv("gameId", gameId),
                        kv("status", currentGame.status)
                    )
                    throw InvalidGameStateException("Failed to start game $gameId")
                }
            }
        }

        val updated = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        log.info(
            "Game started successfully (atomic transition)",
            kv("gameId", gameId),
            kv("name", updated.name),
            kv("startedAt", updated.startedAt)
        )

        gameMetrics.recordGameStarted(gameId)

        // Invalidate active question cache so it gets recalculated with new start time
        activeQuestionCacheService.invalidate(gameId)

        return GameResponse.from(updated)
    }

    override fun completeGame(gameId: UUID, endAt: Instant): GameResponse {
        log.info(
            "Completing game",
            kv("gameId", gameId),
            kv("endAt", endAt)
        )

        // First, check if game exists
        val game = gameRepository.findById(gameId)
            ?: run {
                log.warn("Game not found when completing", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }

        // Atomic transition from ACTIVE -> COMPLETED
        val success = gameRepository.transitionStatus(
            gameId,
            expectedStatus = GameStatus.ACTIVE,
            newStatus = GameStatus.COMPLETED,
            timestamp = endAt
        )

        if (!success) {
            // Re-fetch to get current status for accurate error message
            val currentGame = gameRepository.findById(gameId)
                ?: throw GameNotFoundException(gameId)

            when (currentGame.status) {
                GameStatus.COMPLETED -> {
                    log.warn(
                        "Attempt to complete already completed game (concurrent complete detected)",
                        kv("gameId", gameId),
                        kv("status", currentGame.status)
                    )
                    throw GameAlreadyCompletedException(gameId)
                }
                else -> {
                    log.warn(
                        "Attempt to complete non-active game",
                        kv("gameId", gameId),
                        kv("status", currentGame.status)
                    )
                    throw InvalidGameStateException("Cannot complete game $gameId with status ${currentGame.status}")
                }
            }
        }

        val updated = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        log.info(
            "Game completed successfully (atomic transition)",
            kv("gameId", gameId),
            kv("name", updated.name),
            kv("endedAt", updated.endedAt),
            kv("remainingBudget", updated.remainingBudget)
        )

        gameMetrics.recordGameCompleted(gameId)

        return GameResponse.from(updated)
    }

    override fun deleteGame(gameId: UUID) {
        log.info("Deleting game", kv("gameId", gameId))

        val exists = gameRepository.findById(gameId) != null
        if (!exists) {
            log.warn("Game not found when deleting", kv("gameId", gameId))
            throw GameNotFoundException(gameId)
        }

        gameRepository.delete(gameId)

        log.info("Game deleted successfully", kv("gameId", gameId))
    }
}
package com.gameplatform.game.service.impl

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.dto.GameResponse
import com.gameplatform.game.exception.GameAlreadyCompletedException
import com.gameplatform.game.exception.GameAlreadyStartedException
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InvalidGameStateException
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.GameService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class GameServiceImpl(
    private val gameRepository: GameRepository
) : GameService {

    override fun createGame(request: CreateGameRequest): GameResponse {
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
        return GameResponse.from(saved)
    }

    @Transactional(readOnly = true)
    override fun getGame(gameId: UUID): GameResponse {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)
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
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        when (game.status) {
            GameStatus.ACTIVE -> throw GameAlreadyStartedException(gameId)
            GameStatus.COMPLETED -> throw GameAlreadyCompletedException(gameId)
            GameStatus.DRAFT, GameStatus.SCHEDULED -> {
                // Valid state to start
            }
        }

        val success = gameRepository.updateStatus(gameId, GameStatus.ACTIVE, startAt)
        if (!success) {
            throw InvalidGameStateException("Failed to start game $gameId")
        }

        val updated = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)
        return GameResponse.from(updated)
    }

    override fun completeGame(gameId: UUID, endAt: Instant): GameResponse {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        if (game.status == GameStatus.COMPLETED) {
            throw GameAlreadyCompletedException(gameId)
        }

        if (game.status != GameStatus.ACTIVE) {
            throw InvalidGameStateException("Cannot complete game $gameId with status ${game.status}")
        }

        val success = gameRepository.updateStatus(gameId, GameStatus.COMPLETED, endAt)
        if (!success) {
            throw InvalidGameStateException("Failed to complete game $gameId")
        }

        val updated = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)
        return GameResponse.from(updated)
    }

    override fun deleteGame(gameId: UUID) {
        val exists = gameRepository.findById(gameId) != null
        if (!exists) {
            throw GameNotFoundException(gameId)
        }
        gameRepository.delete(gameId)
    }
}
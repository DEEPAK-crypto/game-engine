package com.gameplatform.game.dto

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateGameRequest(
    val name: String,
    val gameType: GameType,
    val initialBudget: BigDecimal,
    val questionTimerSeconds: Int,
    val scheduledAt: Instant? = null
)

data class GameResponse(
    val id: UUID,
    val name: String,
    val gameType: GameType,
    val initialBudget: BigDecimal,
    val remainingBudget: BigDecimal,
    val status: GameStatus,
    val scheduledAt: Instant?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val questionTimerSeconds: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(game: Game): GameResponse {
            return GameResponse(
                id = game.id,
                name = game.name,
                gameType = game.gameType,
                initialBudget = game.initialBudget,
                remainingBudget = game.remainingBudget,
                status = game.status,
                scheduledAt = game.scheduledAt,
                startedAt = game.startedAt,
                endedAt = game.endedAt,
                questionTimerSeconds = game.questionTimerSeconds,
                createdAt = game.createdAt,
                updatedAt = game.updatedAt
            )
        }
    }
}

data class StartGameRequest(
    val startAt: Instant = Instant.now()
)

data class CompleteGameRequest(
    val endAt: Instant = Instant.now()
)
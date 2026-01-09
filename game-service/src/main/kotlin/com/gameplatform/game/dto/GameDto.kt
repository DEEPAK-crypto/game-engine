package com.gameplatform.game.dto

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateGameRequest(
    @field:NotBlank(message = "Game name is required")
    @field:Size(min = 3, max = 100, message = "Game name must be between 3 and 100 characters")
    val name: String,

    @field:NotNull(message = "Game type is required")
    val gameType: GameType,

    @field:NotNull(message = "Initial budget is required")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "Initial budget must be positive")
    val initialBudget: BigDecimal,

    @field:Min(value = 5, message = "Question timer must be at least 5 seconds")
    @field:Max(value = 300, message = "Question timer must be at most 300 seconds")
    val questionTimerSeconds: Int,

    @field:Future(message = "Scheduled time must be in the future")
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
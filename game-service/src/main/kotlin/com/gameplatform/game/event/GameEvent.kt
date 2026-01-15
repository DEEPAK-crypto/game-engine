package com.gameplatform.game.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Base class for all game events broadcast via WebSocket.
 */
sealed class GameEvent(
    val type: String,
    val gameId: UUID,
    val timestamp: Instant = Instant.now()
)

/**
 * Sent when a game transitions to ACTIVE state.
 */
data class GameStartedEvent(
    override val gameId: UUID,
    val gameName: String,
    val totalQuestions: Int
) : GameEvent("GAME_STARTED", gameId)

/**
 * Sent when a game is completed.
 */
data class GameCompletedEvent(
    override val gameId: UUID,
    val totalParticipants: Int,
    val totalRewardsDistributed: BigDecimal
) : GameEvent("GAME_COMPLETED", gameId)

/**
 * Sent when a question becomes active.
 */
data class QuestionActivatedEvent(
    override val gameId: UUID,
    val questionId: UUID,
    val questionNumber: Int,
    val questionText: String,
    val options: List<QuestionOptionDto>,
    val durationSeconds: Int,
    val expiresAt: Instant
) : GameEvent("QUESTION_ACTIVATED", gameId)

data class QuestionOptionDto(
    val id: UUID,
    val optionText: String,
    val displayOrder: Int
)

/**
 * Sent when a question timer expires.
 */
data class QuestionExpiredEvent(
    override val gameId: UUID,
    val questionId: UUID,
    val correctOptionId: UUID,
    val winnerId: UUID?,
    val winnerReward: BigDecimal?
) : GameEvent("QUESTION_EXPIRED", gameId)

/**
 * Sent to confirm answer submission to the user who submitted.
 */
data class AnswerReceivedEvent(
    override val gameId: UUID,
    val questionId: UUID,
    val userId: UUID,
    val receivedAt: Instant,
    val position: Int  // Position in submission queue
) : GameEvent("ANSWER_RECEIVED", gameId)

/**
 * Sent when a winner is determined for a question.
 */
data class QuestionWinnerEvent(
    override val gameId: UUID,
    val questionId: UUID,
    val winnerId: UUID,
    val winnerName: String?,
    val reward: BigDecimal,
    val answerTimeMs: Long
) : GameEvent("QUESTION_WINNER", gameId)

/**
 * Sent periodically with leaderboard updates.
 */
data class LeaderboardUpdateEvent(
    override val gameId: UUID,
    val topPlayers: List<LeaderboardEntryDto>
) : GameEvent("LEADERBOARD_UPDATE", gameId)

data class LeaderboardEntryDto(
    val rank: Int,
    val userId: UUID,
    val userName: String?,
    val totalRewards: BigDecimal,
    val questionsWon: Int
)

/**
 * Countdown event before game/question starts.
 */
data class CountdownEvent(
    override val gameId: UUID,
    val secondsRemaining: Int,
    val countdownType: CountdownType
) : GameEvent("COUNTDOWN", gameId)

enum class CountdownType {
    GAME_START,
    QUESTION_START,
    QUESTION_END
}

/**
 * Player count update.
 */
data class PlayerCountEvent(
    override val gameId: UUID,
    val connectedPlayers: Int
) : GameEvent("PLAYER_COUNT", gameId)
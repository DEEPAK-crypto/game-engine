package com.gameplatform.game.dto

import com.gameplatform.game.cassandra.entity.UserGameResult
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for question leaderboard entries.
 * Data is sourced from Redis sorted sets.
 */
data class QuestionLeaderboardResponse(
    val rank: Int,
    val userId: UUID,
    val rewardAmount: BigDecimal,
    val answeredAt: Instant
)

/**
 * Response DTO for game leaderboard entries.
 * Data is sourced from Redis sorted sets.
 */
data class GameLeaderboardResponse(
    val rank: Int,
    val userId: UUID,
    val totalReward: BigDecimal,
    val correctAnswers: Int
)

/**
 * Response DTO for user game results.
 * Data is sourced from Cassandra user_game_results table.
 */
data class UserGameResultResponse(
    val userId: UUID,
    val gameId: UUID,
    val totalReward: BigDecimal,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val finalRank: Int
) {
    companion object {
        fun from(result: UserGameResult): UserGameResultResponse {
            return UserGameResultResponse(
                userId = result.userId,
                gameId = result.gameId,
                totalReward = result.totalReward,
                correctAnswers = result.correctAnswers,
                totalQuestions = result.totalQuestions,
                finalRank = result.finalRank
            )
        }
    }
}
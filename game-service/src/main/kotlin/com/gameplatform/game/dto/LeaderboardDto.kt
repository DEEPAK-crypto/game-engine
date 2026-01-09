package com.gameplatform.game.dto

import com.gameplatform.game.cassandra.entity.GameLeaderboard
import com.gameplatform.game.cassandra.entity.QuestionLeaderboard
import com.gameplatform.game.cassandra.entity.UserGameResult
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class QuestionLeaderboardResponse(
    val rank: Int,
    val userId: UUID,
    val rewardAmount: BigDecimal,
    val answeredAt: Instant
) {
    companion object {
        fun from(entry: QuestionLeaderboard): QuestionLeaderboardResponse {
            return QuestionLeaderboardResponse(
                rank = entry.rank,
                userId = entry.userId,
                rewardAmount = entry.rewardAmount,
                answeredAt = entry.answeredAt
            )
        }
    }
}

data class GameLeaderboardResponse(
    val rank: Int,
    val userId: UUID,
    val totalReward: BigDecimal,
    val correctAnswers: Int
) {
    companion object {
        fun from(entry: GameLeaderboard): GameLeaderboardResponse {
            return GameLeaderboardResponse(
                rank = entry.rank,
                userId = entry.userId,
                totalReward = entry.totalReward,
                correctAnswers = entry.correctAnswers
            )
        }
    }
}

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
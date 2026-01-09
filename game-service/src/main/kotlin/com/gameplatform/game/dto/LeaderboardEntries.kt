package com.gameplatform.game.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Represents a user's entry in a question leaderboard.
 */
data class QuestionLeaderboardEntry(
    val rank: Int,
    val userId: UUID,
    val rewardAmount: BigDecimal,
    val answeredAt: Instant
)

/**
 * Represents a user's entry in a game leaderboard.
 */
data class GameLeaderboardEntry(
    val rank: Int,
    val userId: UUID,
    val totalReward: BigDecimal,
    val lastUpdated: Instant
)
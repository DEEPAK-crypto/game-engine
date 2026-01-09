package com.gameplatform.game.service

import com.gameplatform.game.dto.GameLeaderboardEntry
import com.gameplatform.game.dto.QuestionLeaderboardEntry
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Service for managing real-time leaderboards using Redis sorted sets.
 *
 * Redis sorted sets provide O(log N) complexity for updates and range queries,
 * making them ideal for high-performance leaderboard operations.
 *
 * Key patterns:
 * - Question leaderboard: leaderboard:question:{gameId}:{questionId}
 * - Game leaderboard: leaderboard:game:{gameId}
 *
 * Scores are composed of:
 * - Primary: reward amount (multiplied by 1e10 for precision)
 * - Tiebreaker: timestamp (microseconds since epoch, negated for chronological order)
 */
interface RedisLeaderboardService {

    /**
     * Adds a user to the question leaderboard.
     *
     * @param gameId The game ID
     * @param questionId The question ID
     * @param userId The user ID
     * @param rewardAmount The reward amount received
     * @param answeredAt The timestamp when the answer was submitted
     */
    fun addToQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        userId: UUID,
        rewardAmount: BigDecimal,
        answeredAt: Instant
    )

    /**
     * Updates or adds a user to the game leaderboard.
     *
     * @param gameId The game ID
     * @param userId The user ID
     * @param totalReward The user's total reward in the game
     * @param lastUpdated The timestamp of the last update
     */
    fun updateGameLeaderboard(
        gameId: UUID,
        userId: UUID,
        totalReward: BigDecimal,
        lastUpdated: Instant
    )

    /**
     * Gets the top N users from a question leaderboard.
     *
     * @param gameId The game ID
     * @param questionId The question ID
     * @param limit The number of users to retrieve
     * @return List of leaderboard entries ordered by rank
     */
    fun getQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        limit: Int
    ): List<QuestionLeaderboardEntry>

    /**
     * Gets the top N users from a game leaderboard.
     *
     * @param gameId The game ID
     * @param limit The number of users to retrieve
     * @return List of leaderboard entries ordered by rank
     */
    fun getGameLeaderboard(
        gameId: UUID,
        limit: Int
    ): List<GameLeaderboardEntry>

    /**
     * Gets a user's rank in a question leaderboard.
     *
     * @param gameId The game ID
     * @param questionId The question ID
     * @param userId The user ID
     * @return The user's rank (1-based) or null if not found
     */
    fun getUserQuestionRank(
        gameId: UUID,
        questionId: UUID,
        userId: UUID
    ): Int?

    /**
     * Gets a user's rank in a game leaderboard.
     *
     * @param gameId The game ID
     * @param userId The user ID
     * @return The user's rank (1-based) or null if not found
     */
    fun getUserGameRank(
        gameId: UUID,
        userId: UUID
    ): Int?

    /**
     * Clears a question leaderboard from Redis.
     *
     * @param gameId The game ID
     * @param questionId The question ID
     */
    fun clearQuestionLeaderboard(gameId: UUID, questionId: UUID)

    /**
     * Clears a game leaderboard from Redis.
     *
     * @param gameId The game ID
     */
    fun clearGameLeaderboard(gameId: UUID)
}
package com.gameplatform.game.service.impl

import com.gameplatform.game.dto.GameLeaderboardEntry
import com.gameplatform.game.dto.QuestionLeaderboardEntry
import com.gameplatform.game.service.RedisLeaderboardService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Service
class RedisLeaderboardServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>
) : RedisLeaderboardService {

    private val log = LoggerFactory.getLogger(RedisLeaderboardServiceImpl::class.java)

    companion object {
        private const val QUESTION_LEADERBOARD_PREFIX = "leaderboard:question"
        private const val GAME_LEADERBOARD_PREFIX = "leaderboard:game"

        // Multiplier for reward amount to preserve precision in scores
        private const val REWARD_MULTIPLIER = 1_000_000_000_000.0 // 1e12

        // Maximum timestamp value for calculating tie-breaker
        private const val MAX_TIMESTAMP_MICROS = 9_999_999_999_999_999L
    }

    override fun addToQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        userId: UUID,
        rewardAmount: BigDecimal,
        answeredAt: Instant
    ) {
        val key = getQuestionLeaderboardKey(gameId, questionId)
        val score = calculateScore(rewardAmount, answeredAt)

        log.debug(
            "Adding user to question leaderboard in Redis",
            kv("gameId", gameId),
            kv("questionId", questionId),
            kv("userId", userId),
            kv("rewardAmount", rewardAmount),
            kv("score", score)
        )

        redisTemplate.opsForZSet().add(key, userId.toString(), score)
    }

    override fun updateGameLeaderboard(
        gameId: UUID,
        userId: UUID,
        totalReward: BigDecimal,
        lastUpdated: Instant
    ) {
        val key = getGameLeaderboardKey(gameId)
        val score = calculateScore(totalReward, lastUpdated)

        log.debug(
            "Updating user in game leaderboard in Redis",
            kv("gameId", gameId),
            kv("userId", userId),
            kv("totalReward", totalReward),
            kv("score", score)
        )

        redisTemplate.opsForZSet().add(key, userId.toString(), score)
    }

    override fun getQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        limit: Int
    ): List<QuestionLeaderboardEntry> {
        val key = getQuestionLeaderboardKey(gameId, questionId)

        log.debug(
            "Retrieving question leaderboard from Redis",
            kv("gameId", gameId),
            kv("questionId", questionId),
            kv("limit", limit)
        )

        // Get top N users with highest scores (reverse range)
        val entries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, (limit - 1).toLong())
            ?: emptySet()

        return entries.mapIndexed { index, entry ->
            val userId = UUID.fromString(entry.value as String)
            val score = entry.score ?: 0.0
            val (rewardAmount, answeredAt) = parseScore(score)

            QuestionLeaderboardEntry(
                rank = index + 1,
                userId = userId,
                rewardAmount = rewardAmount,
                answeredAt = answeredAt
            )
        }
    }

    override fun getGameLeaderboard(
        gameId: UUID,
        limit: Int
    ): List<GameLeaderboardEntry> {
        val key = getGameLeaderboardKey(gameId)

        log.debug(
            "Retrieving game leaderboard from Redis",
            kv("gameId", gameId),
            kv("limit", limit)
        )

        // Get top N users with highest scores (reverse range)
        val entries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, (limit - 1).toLong())
            ?: emptySet()

        return entries.mapIndexed { index, entry ->
            val userId = UUID.fromString(entry.value as String)
            val score = entry.score ?: 0.0
            val (totalReward, lastUpdated) = parseScore(score)

            GameLeaderboardEntry(
                rank = index + 1,
                userId = userId,
                totalReward = totalReward,
                lastUpdated = lastUpdated
            )
        }
    }

    override fun getUserQuestionRank(
        gameId: UUID,
        questionId: UUID,
        userId: UUID
    ): Int? {
        val key = getQuestionLeaderboardKey(gameId, questionId)

        // Get reverse rank (higher scores = lower rank numbers)
        val rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString())

        // Rank is 0-based, convert to 1-based
        return rank?.let { (it + 1).toInt() }
    }

    override fun getUserGameRank(
        gameId: UUID,
        userId: UUID
    ): Int? {
        val key = getGameLeaderboardKey(gameId)

        // Get reverse rank (higher scores = lower rank numbers)
        val rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString())

        // Rank is 0-based, convert to 1-based
        return rank?.let { (it + 1).toInt() }
    }

    override fun clearQuestionLeaderboard(gameId: UUID, questionId: UUID) {
        val key = getQuestionLeaderboardKey(gameId, questionId)
        log.info(
            "Clearing question leaderboard from Redis",
            kv("gameId", gameId),
            kv("questionId", questionId)
        )
        redisTemplate.delete(key)
    }

    override fun clearGameLeaderboard(gameId: UUID) {
        val key = getGameLeaderboardKey(gameId)
        log.info("Clearing game leaderboard from Redis", kv("gameId", gameId))
        redisTemplate.delete(key)
    }

    /**
     * Calculates a Redis sorted set score that encodes both reward amount and timestamp.
     *
     * The score is calculated as:
     * - Primary component: reward amount * 1e12 (to preserve precision)
     * - Tiebreaker: (MAX_TIMESTAMP - timestamp_micros) / 1e15 (earlier timestamps get higher scores)
     *
     * This ensures users are ranked first by reward amount, then by who answered first.
     */
    private fun calculateScore(rewardAmount: BigDecimal, timestamp: Instant): Double {
        val rewardScore = rewardAmount.toDouble() * REWARD_MULTIPLIER

        // Convert timestamp to microseconds and negate so earlier times = higher scores
        val timestampMicros = timestamp.epochSecond * 1_000_000 + timestamp.nano / 1_000
        val timestampScore = (MAX_TIMESTAMP_MICROS - timestampMicros) / 1_000_000_000_000_000.0

        return rewardScore + timestampScore
    }

    /**
     * Parses a score back into reward amount and timestamp.
     *
     * This is an approximation for display purposes.
     */
    private fun parseScore(score: Double): Pair<BigDecimal, Instant> {
        // Extract reward amount (primary component)
        val rewardAmount = BigDecimal.valueOf(score / REWARD_MULTIPLIER)
            .setScale(2, BigDecimal.ROUND_HALF_UP)

        // Extract timestamp (tiebreaker component)
        val timestampScore = score % REWARD_MULTIPLIER
        val timestampMicros = MAX_TIMESTAMP_MICROS - (timestampScore * 1_000_000_000_000_000.0).toLong()
        val epochSecond = timestampMicros / 1_000_000
        val nanoAdjustment = ((timestampMicros % 1_000_000) * 1_000).toLong()
        val timestamp = Instant.ofEpochSecond(epochSecond, nanoAdjustment)

        return Pair(rewardAmount, timestamp)
    }

    private fun getQuestionLeaderboardKey(gameId: UUID, questionId: UUID): String {
        return "$QUESTION_LEADERBOARD_PREFIX:$gameId:$questionId"
    }

    private fun getGameLeaderboardKey(gameId: UUID): String {
        return "$GAME_LEADERBOARD_PREFIX:$gameId"
    }
}
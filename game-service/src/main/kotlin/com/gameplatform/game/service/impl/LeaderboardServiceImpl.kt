package com.gameplatform.game.service.impl

import com.gameplatform.game.cassandra.repository.UserGameResultRepository
import com.gameplatform.game.dto.GameLeaderboardResponse
import com.gameplatform.game.dto.QuestionLeaderboardResponse
import com.gameplatform.game.dto.UserGameResultResponse
import com.gameplatform.game.service.LeaderboardService
import com.gameplatform.game.service.RedisLeaderboardService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LeaderboardServiceImpl(
    private val userGameResultRepository: UserGameResultRepository,
    private val redisLeaderboardService: RedisLeaderboardService
) : LeaderboardService {

    private val log = LoggerFactory.getLogger(LeaderboardServiceImpl::class.java)

    @Transactional(readOnly = true)
    override fun getQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        limit: Int
    ): List<QuestionLeaderboardResponse> {
        log.debug(
            "Retrieving question leaderboard from Redis",
            kv("gameId", gameId),
            kv("questionId", questionId),
            kv("limit", limit)
        )

        // Get leaderboard from Redis (fast O(log N) query)
        val redisEntries = redisLeaderboardService.getQuestionLeaderboard(gameId, questionId, limit)

        // Convert to response format
        val results = redisEntries.map { entry ->
            QuestionLeaderboardResponse(
                rank = entry.rank,
                userId = entry.userId,
                rewardAmount = entry.rewardAmount,
                answeredAt = entry.answeredAt
            )
        }

        log.debug("Question leaderboard retrieved from Redis", kv("entryCount", results.size))
        return results
    }

    @Transactional(readOnly = true)
    override fun getGameLeaderboard(gameId: UUID, limit: Int): List<GameLeaderboardResponse> {
        log.debug(
            "Retrieving game leaderboard from Redis",
            kv("gameId", gameId),
            kv("limit", limit)
        )

        // Get leaderboard from Redis (fast O(log N) query)
        val redisEntries = redisLeaderboardService.getGameLeaderboard(gameId, limit)

        // Convert to response format
        val results = redisEntries.map { entry ->
            GameLeaderboardResponse(
                rank = entry.rank,
                userId = entry.userId,
                totalReward = entry.totalReward,
                correctAnswers = 0  // Not stored in Redis, set to 0
            )
        }

        log.debug("Game leaderboard retrieved from Redis", kv("entryCount", results.size))
        return results
    }

    @Transactional(readOnly = true)
    override fun getUserGameResult(userId: UUID, gameId: UUID): UserGameResultResponse? {
        log.debug(
            "Retrieving user game result",
            kv("userId", userId),
            kv("gameId", gameId)
        )
        val result = userGameResultRepository.findByUserIdAndGameId(userId, gameId)
            ?: run {
                log.debug(
                    "User game result not found",
                    kv("userId", userId),
                    kv("gameId", gameId)
                )
                return null
            }
        return UserGameResultResponse.from(result)
    }
}
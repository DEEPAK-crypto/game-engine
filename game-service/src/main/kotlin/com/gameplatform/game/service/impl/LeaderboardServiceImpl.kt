package com.gameplatform.game.service.impl

import com.gameplatform.game.cassandra.entity.GameLeaderboard
import com.gameplatform.game.cassandra.entity.UserGameResult
import com.gameplatform.game.cassandra.repository.GameLeaderboardRepository
import com.gameplatform.game.cassandra.repository.QuestionLeaderboardRepository
import com.gameplatform.game.cassandra.repository.UserGameResultRepository
import com.gameplatform.game.cassandra.repository.UserQuestionAnswerRepository
import com.gameplatform.game.dto.GameLeaderboardResponse
import com.gameplatform.game.dto.QuestionLeaderboardResponse
import com.gameplatform.game.dto.UserGameResultResponse
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.LeaderboardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class LeaderboardServiceImpl(
    private val questionLeaderboardRepository: QuestionLeaderboardRepository,
    private val gameLeaderboardRepository: GameLeaderboardRepository,
    private val userGameResultRepository: UserGameResultRepository,
    private val userQuestionAnswerRepository: UserQuestionAnswerRepository,
    private val gameRepository: GameRepository,
    private val questionRepository: QuestionRepository
) : LeaderboardService {

    @Transactional(readOnly = true)
    override fun getQuestionLeaderboard(
        gameId: UUID,
        questionId: UUID,
        limit: Int
    ): List<QuestionLeaderboardResponse> {
        return questionLeaderboardRepository
            .findTopNByGameIdAndQuestionId(gameId, questionId, limit)
            .map { QuestionLeaderboardResponse.from(it) }
    }

    @Transactional(readOnly = true)
    override fun getGameLeaderboard(gameId: UUID, limit: Int): List<GameLeaderboardResponse> {
        return gameLeaderboardRepository
            .findTopNByGameId(gameId, limit)
            .map { GameLeaderboardResponse.from(it) }
    }

    @Transactional
    override fun updateGameLeaderboard(gameId: UUID) {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        val questions = questionRepository.findByGameIdOrderByIndex(gameId)
        val totalQuestions = questions.size

        // Get all user answers for this game
        val userStats = mutableMapOf<UUID, UserStats>()

        questions.forEach { question ->
            val leaderboard = questionLeaderboardRepository
                .findByGameIdAndQuestionIdOrderByRank(gameId, question.id)

            leaderboard.forEach { entry ->
                val stats = userStats.getOrPut(entry.userId) { UserStats() }
                stats.totalReward += entry.rewardAmount
                if (entry.rewardAmount > BigDecimal.ZERO) {
                    stats.correctAnswers++
                }
            }
        }

        // Sort users by total reward (descending) and correct answers (descending)
        val sortedUsers = userStats.entries
            .sortedWith(
                compareByDescending<Map.Entry<UUID, UserStats>> { it.value.totalReward }
                    .thenByDescending { it.value.correctAnswers }
            )

        // Save game leaderboard
        sortedUsers.forEachIndexed { index, entry ->
            val rank = index + 1
            val leaderboardEntry = GameLeaderboard(
                gameId = gameId,
                rank = rank,
                userId = entry.key,
                totalReward = entry.value.totalReward,
                correctAnswers = entry.value.correctAnswers
            )
            gameLeaderboardRepository.save(leaderboardEntry)

            // Save user game result
            val userResult = UserGameResult(
                userId = entry.key,
                gameId = gameId,
                totalReward = entry.value.totalReward,
                correctAnswers = entry.value.correctAnswers,
                totalQuestions = totalQuestions,
                finalRank = rank
            )
            userGameResultRepository.save(userResult)
        }
    }

    @Transactional(readOnly = true)
    override fun getUserGameResult(userId: UUID, gameId: UUID): UserGameResultResponse? {
        val result = userGameResultRepository.findByUserIdAndGameId(userId, gameId)
            ?: return null
        return UserGameResultResponse.from(result)
    }

    private data class UserStats(
        var totalReward: BigDecimal = BigDecimal.ZERO,
        var correctAnswers: Int = 0
    )
}
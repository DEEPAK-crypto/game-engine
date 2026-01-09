package com.gameplatform.game.service

import com.gameplatform.game.dto.GameLeaderboardResponse
import com.gameplatform.game.dto.QuestionLeaderboardResponse
import com.gameplatform.game.dto.UserGameResultResponse
import java.util.UUID

interface LeaderboardService {
    /**
     * Get leaderboard for a specific question from Redis.
     */
    fun getQuestionLeaderboard(gameId: UUID, questionId: UUID, limit: Int = 10): List<QuestionLeaderboardResponse>

    /**
     * Get overall game leaderboard from Redis.
     */
    fun getGameLeaderboard(gameId: UUID, limit: Int = 10): List<GameLeaderboardResponse>

    /**
     * Get user's result for a specific game from Cassandra.
     */
    fun getUserGameResult(userId: UUID, gameId: UUID): UserGameResultResponse?
}
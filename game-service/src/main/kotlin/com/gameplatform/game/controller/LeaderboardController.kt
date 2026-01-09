package com.gameplatform.game.controller

import com.gameplatform.game.dto.GameLeaderboardResponse
import com.gameplatform.game.dto.QuestionLeaderboardResponse
import com.gameplatform.game.dto.UserGameResultResponse
import com.gameplatform.game.service.LeaderboardService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/leaderboards")
class LeaderboardController(
    private val leaderboardService: LeaderboardService
) {

    @GetMapping("/games/{gameId}")
    fun getGameLeaderboard(
        @PathVariable gameId: UUID,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<GameLeaderboardResponse> {
        return leaderboardService.getGameLeaderboard(gameId, limit)
    }

    @GetMapping("/games/{gameId}/questions/{questionId}")
    fun getQuestionLeaderboard(
        @PathVariable gameId: UUID,
        @PathVariable questionId: UUID,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<QuestionLeaderboardResponse> {
        return leaderboardService.getQuestionLeaderboard(gameId, questionId, limit)
    }

    @GetMapping("/users/{userId}/games/{gameId}")
    fun getUserGameResult(
        @PathVariable userId: UUID,
        @PathVariable gameId: UUID
    ): UserGameResultResponse? {
        return leaderboardService.getUserGameResult(userId, gameId)
    }
}
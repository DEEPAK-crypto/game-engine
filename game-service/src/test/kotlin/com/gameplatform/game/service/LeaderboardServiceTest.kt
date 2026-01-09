package com.gameplatform.game.service

import com.gameplatform.game.cassandra.entity.UserGameResult
import com.gameplatform.game.cassandra.repository.UserGameResultRepository
import com.gameplatform.game.dto.GameLeaderboardEntry
import com.gameplatform.game.dto.QuestionLeaderboardEntry
import com.gameplatform.game.service.impl.LeaderboardServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class LeaderboardServiceTest {

    private lateinit var leaderboardService: LeaderboardService
    private lateinit var userGameResultRepository: UserGameResultRepository
    private lateinit var redisLeaderboardService: RedisLeaderboardService

    @BeforeEach
    fun setup() {
        userGameResultRepository = mock()
        redisLeaderboardService = mock()

        leaderboardService = LeaderboardServiceImpl(
            userGameResultRepository,
            redisLeaderboardService
        )
    }

    @Test
    fun `should get question leaderboard from Redis`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val limit = 10
        val now = Instant.now()

        val redisEntries = listOf(
            QuestionLeaderboardEntry(1, UUID.randomUUID(), BigDecimal("100.00"), now),
            QuestionLeaderboardEntry(2, UUID.randomUUID(), BigDecimal("75.00"), now.plusSeconds(1)),
            QuestionLeaderboardEntry(3, UUID.randomUUID(), BigDecimal("50.00"), now.plusSeconds(2))
        )

        whenever(redisLeaderboardService.getQuestionLeaderboard(gameId, questionId, limit))
            .thenReturn(redisEntries)

        // When
        val result = leaderboardService.getQuestionLeaderboard(gameId, questionId, limit)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].rewardAmount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result[1].rank).isEqualTo(2)
        assertThat(result[2].rank).isEqualTo(3)

        verify(redisLeaderboardService).getQuestionLeaderboard(gameId, questionId, limit)
    }

    @Test
    fun `should get game leaderboard from Redis`() {
        // Given
        val gameId = UUID.randomUUID()
        val limit = 5
        val now = Instant.now()

        val redisEntries = listOf(
            GameLeaderboardEntry(1, UUID.randomUUID(), BigDecimal("250.00"), now),
            GameLeaderboardEntry(2, UUID.randomUUID(), BigDecimal("150.00"), now.plusSeconds(1))
        )

        whenever(redisLeaderboardService.getGameLeaderboard(gameId, limit))
            .thenReturn(redisEntries)

        // When
        val result = leaderboardService.getGameLeaderboard(gameId, limit)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(result[0].totalReward).isEqualByComparingTo(BigDecimal("250.00"))
        assertThat(result[1].rank).isEqualTo(2)

        verify(redisLeaderboardService).getGameLeaderboard(gameId, limit)
    }

    @Test
    fun `should return empty list when no leaderboard entries exist`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val limit = 10

        whenever(redisLeaderboardService.getQuestionLeaderboard(gameId, questionId, limit))
            .thenReturn(emptyList())

        // When
        val result = leaderboardService.getQuestionLeaderboard(gameId, questionId, limit)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should get user game result from Cassandra`() {
        // Given
        val userId = UUID.randomUUID()
        val gameId = UUID.randomUUID()
        val userGameResult = UserGameResult(
            userId = userId,
            gameId = gameId,
            totalReward = BigDecimal("175.00"),
            correctAnswers = 3,
            totalQuestions = 5,
            finalRank = 2
        )

        whenever(userGameResultRepository.findByUserIdAndGameId(userId, gameId))
            .thenReturn(userGameResult)

        // When
        val result = leaderboardService.getUserGameResult(userId, gameId)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.userId).isEqualTo(userId)
        assertThat(result.gameId).isEqualTo(gameId)
        assertThat(result.totalReward).isEqualByComparingTo(BigDecimal("175.00"))
        assertThat(result.correctAnswers).isEqualTo(3)
        assertThat(result.finalRank).isEqualTo(2)
    }

    @Test
    fun `should return null when user game result not found`() {
        // Given
        val userId = UUID.randomUUID()
        val gameId = UUID.randomUUID()

        whenever(userGameResultRepository.findByUserIdAndGameId(userId, gameId))
            .thenReturn(null)

        // When
        val result = leaderboardService.getUserGameResult(userId, gameId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should respect limit parameter for question leaderboard`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val limit = 3
        val now = Instant.now()

        val redisEntries = (1..3).map { i ->
            QuestionLeaderboardEntry(i, UUID.randomUUID(), BigDecimal(100 - (i * 10)), now.plusSeconds(i.toLong()))
        }

        whenever(redisLeaderboardService.getQuestionLeaderboard(gameId, questionId, limit))
            .thenReturn(redisEntries)

        // When
        val result = leaderboardService.getQuestionLeaderboard(gameId, questionId, limit)

        // Then
        assertThat(result).hasSize(3)
        verify(redisLeaderboardService).getQuestionLeaderboard(gameId, questionId, limit)
    }

    @Test
    fun `should use default limit of 10 when not specified`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()

        whenever(redisLeaderboardService.getQuestionLeaderboard(gameId, questionId, 10))
            .thenReturn(emptyList())

        // When
        leaderboardService.getQuestionLeaderboard(gameId, questionId)

        // Then
        verify(redisLeaderboardService).getQuestionLeaderboard(gameId, questionId, 10)
    }
}

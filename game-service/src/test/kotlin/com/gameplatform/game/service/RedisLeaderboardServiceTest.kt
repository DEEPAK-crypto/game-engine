package com.gameplatform.game.service

import com.gameplatform.game.service.impl.RedisLeaderboardServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class
RedisLeaderboardServiceTest {

    private lateinit var redisLeaderboardService: RedisLeaderboardService
    private lateinit var redisTemplate: RedisTemplate<String, Any>
    private lateinit var zSetOps: ZSetOperations<String, Any>

    private lateinit var testGameId: UUID
    private lateinit var testQuestionId: UUID
    private lateinit var testUserId1: UUID
    private lateinit var testUserId2: UUID
    private lateinit var testUserId3: UUID

    @BeforeEach
    fun setup() {
        redisTemplate = mock()
        zSetOps = mock()

        whenever(redisTemplate.opsForZSet()).thenReturn(zSetOps)

        redisLeaderboardService = RedisLeaderboardServiceImpl(redisTemplate)

        testGameId = UUID.randomUUID()
        testQuestionId = UUID.randomUUID()
        testUserId1 = UUID.randomUUID()
        testUserId2 = UUID.randomUUID()
        testUserId3 = UUID.randomUUID()
    }

    @Test
    fun `should add users to question leaderboard with correct ranking`() {
        // Given
        val now = Instant.now()
        val key = "leaderboard:question:$testGameId:$testQuestionId"

        // When
        redisLeaderboardService.addToQuestionLeaderboard(
            testGameId, testQuestionId, testUserId1, BigDecimal("100.00"), now
        )

        // Then
        verify(zSetOps).add(eq(key), eq(testUserId1.toString()), any())
    }

    @Test
    fun `should update game leaderboard with total rewards`() {
        // Given
        val now = Instant.now()
        val key = "leaderboard:game:$testGameId"

        // When
        redisLeaderboardService.updateGameLeaderboard(
            testGameId, testUserId1, BigDecimal("250.00"), now
        )

        // Then
        verify(zSetOps).add(eq(key), eq(testUserId1.toString()), any())
    }

    @Test
    fun `should get user's rank in question leaderboard`() {
        // Given
        val key = "leaderboard:question:$testGameId:$testQuestionId"
        whenever(zSetOps.reverseRank(key, testUserId1.toString())).thenReturn(0L) // First place

        // When
        val rank = redisLeaderboardService.getUserQuestionRank(testGameId, testQuestionId, testUserId1)

        // Then
        assertThat(rank).isEqualTo(1) // Rank is 1-indexed
    }

    @Test
    fun `should return null rank for user not in leaderboard`() {
        // Given
        val key = "leaderboard:question:$testGameId:$testQuestionId"
        whenever(zSetOps.reverseRank(key, testUserId1.toString())).thenReturn(null)

        // When
        val rank = redisLeaderboardService.getUserQuestionRank(testGameId, testQuestionId, testUserId1)

        // Then
        assertThat(rank).isNull()
    }

    @Test
    fun `should clear question leaderboard`() {
        // Given
        val key = "leaderboard:question:$testGameId:$testQuestionId"

        // When
        redisLeaderboardService.clearQuestionLeaderboard(testGameId, testQuestionId)

        // Then
        verify(redisTemplate).delete(key)
    }

    @Test
    fun `should clear game leaderboard`() {
        // Given
        val key = "leaderboard:game:$testGameId"

        // When
        redisLeaderboardService.clearGameLeaderboard(testGameId)

        // Then
        verify(redisTemplate).delete(key)
    }

    @Test
    fun `should get question leaderboard with limit`() {
        // Given
        val key = "leaderboard:question:$testGameId:$testQuestionId"
        val tuple1 = mock<ZSetOperations.TypedTuple<Any>>()
        val tuple2 = mock<ZSetOperations.TypedTuple<Any>>()

        whenever(tuple1.value).thenReturn(testUserId1.toString())
        whenever(tuple1.score).thenReturn(1000000000000000.0)
        whenever(tuple2.value).thenReturn(testUserId2.toString())
        whenever(tuple2.score).thenReturn(500000000000000.0)

        val tuples = linkedSetOf(tuple1, tuple2)
        whenever(zSetOps.reverseRangeWithScores(key, 0, 9)).thenReturn(tuples)

        // When
        val leaderboard = redisLeaderboardService.getQuestionLeaderboard(testGameId, testQuestionId, 10)

        // Then
        assertThat(leaderboard).hasSize(2)
        assertThat(leaderboard[0].rank).isEqualTo(1)
        assertThat(leaderboard[1].rank).isEqualTo(2)
    }

    @Test
    fun `should get game leaderboard with limit`() {
        // Given
        val key = "leaderboard:game:$testGameId"
        val tuple1 = mock<ZSetOperations.TypedTuple<Any>>()
        val tuple2 = mock<ZSetOperations.TypedTuple<Any>>()

        whenever(tuple1.value).thenReturn(testUserId1.toString())
        whenever(tuple1.score).thenReturn(2500000000000000.0)
        whenever(tuple2.value).thenReturn(testUserId2.toString())
        whenever(tuple2.score).thenReturn(1500000000000000.0)

        val tuples = linkedSetOf(tuple1, tuple2)
        whenever(zSetOps.reverseRangeWithScores(key, 0, 9)).thenReturn(tuples)

        // When
        val leaderboard = redisLeaderboardService.getGameLeaderboard(testGameId, 10)

        // Then
        assertThat(leaderboard).hasSize(2)
        assertThat(leaderboard[0].rank).isEqualTo(1)
        assertThat(leaderboard[1].rank).isEqualTo(2)
    }

    @Test
    fun `should handle empty leaderboard`() {
        // Given
        val key = "leaderboard:question:$testGameId:$testQuestionId"
        whenever(zSetOps.reverseRangeWithScores(key, 0, 9)).thenReturn(emptySet())

        // When
        val leaderboard = redisLeaderboardService.getQuestionLeaderboard(testGameId, testQuestionId, 10)

        // Then
        assertThat(leaderboard).isEmpty()
    }

    @Test
    fun `should get user game rank`() {
        // Given
        val key = "leaderboard:game:$testGameId"
        whenever(zSetOps.reverseRank(key, testUserId1.toString())).thenReturn(2L) // Third place

        // When
        val rank = redisLeaderboardService.getUserGameRank(testGameId, testUserId1)

        // Then
        assertThat(rank).isEqualTo(3) // Rank is 1-indexed
    }
}

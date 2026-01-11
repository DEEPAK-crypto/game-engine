package com.gameplatform.game.concurrency

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.RedisLeaderboardService
import com.gameplatform.game.cassandra.repository.UserQuestionAnswerRepository
import com.gameplatform.game.testconfig.TestcontainersConfiguration
import com.gameplatform.game.testutil.TestDataFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for race condition handling in the multiplayer game platform.
 *
 * These tests verify that the atomic operations prevent race conditions under
 * concurrent load, as documented in RACE-CONDITION-SOLUTIONS.md.
 *
 * Race conditions tested:
 * 1. Leaderboard Race Condition - Redis Lua script atomicity
 * 2. Budget Race Condition - Atomic SQL UPDATE with WHERE clause
 * 3. Duplicate Answer Race Condition - Cassandra LWT (INSERT IF NOT EXISTS)
 * 4. Game Status Transition Race Condition - Conditional UPDATE
 * 5. User Total Reward Race Condition - Redis INCRBYFLOAT
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class RaceConditionIntegrationTest {

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var redisLeaderboardService: RedisLeaderboardService

    @Autowired
    private lateinit var userQuestionAnswerRepository: UserQuestionAnswerRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun cleanup() {
        // Clean up Redis keys
        redisTemplate.keys("leaderboard:*")?.forEach { redisTemplate.delete(it) }
        redisTemplate.keys("winners:*")?.forEach { redisTemplate.delete(it) }
        redisTemplate.keys("user_total_reward:*")?.forEach { redisTemplate.delete(it) }
    }

    /**
     * Helper to parse Redis value to Double - handles both String and Number types
     */
    private fun parseRedisValue(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDouble()
            null -> throw IllegalStateException("Value not found in Redis")
            else -> throw IllegalStateException("Unexpected Redis value type: ${value::class}")
        }
    }

    @Nested
    @DisplayName("1. Leaderboard Race Condition Tests")
    inner class LeaderboardRaceConditionTests {

        /**
         * Tests that when multiple users submit correct answers simultaneously,
         * only exactly N users claim winner slots (not more, not less).
         *
         * Without the Lua script, the race condition would allow multiple users
         * to see rank 1 and all claim rewards.
         */
        @Test
        @DisplayName("Should allow exactly maxWinners to claim winner slots under concurrent load")
        fun `concurrent submissions should allow exactly maxWinners to claim slots`() {
            // Given
            val gameId = UUID.randomUUID()
            val questionId = UUID.randomUUID()
            val maxWinners = 1
            val concurrentUsers = 50
            val barrier = CyclicBarrier(concurrentUsers)
            val completionLatch = CountDownLatch(concurrentUsers)
            val executor = Executors.newFixedThreadPool(concurrentUsers)

            val successfulClaims = AtomicInteger(0)
            val userIds = (1..concurrentUsers).map { UUID.randomUUID() }

            // When - all users submit simultaneously
            userIds.forEach { userId ->
                executor.submit {
                    try {
                        barrier.await() // Synchronize all threads to start together
                        val answeredAt = Instant.now()

                        val result = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
                            gameId = gameId,
                            questionId = questionId,
                            userId = userId,
                            answeredAt = answeredAt,
                            maxWinners = maxWinners
                        )

                        if (result.claimedWinnerSlot) {
                            successfulClaims.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - exactly maxWinners users should have claimed slots
            assertThat(successfulClaims.get())
                .describedAs("Exactly $maxWinners user(s) should claim winner slot(s)")
                .isEqualTo(maxWinners)
        }

        @Test
        @DisplayName("Should allow exactly 3 winners when maxWinners is 3")
        fun `concurrent submissions should allow exactly 3 winners when maxWinners is 3`() {
            // Given
            val gameId = UUID.randomUUID()
            val questionId = UUID.randomUUID()
            val maxWinners = 3
            val concurrentUsers = 100
            val barrier = CyclicBarrier(concurrentUsers)
            val completionLatch = CountDownLatch(concurrentUsers)
            val executor = Executors.newFixedThreadPool(concurrentUsers)

            val successfulClaims = AtomicInteger(0)
            val claimedUserIds = mutableSetOf<UUID>()
            val lock = Object()

            // When
            (1..concurrentUsers).forEach { i ->
                val userId = UUID.randomUUID()
                executor.submit {
                    try {
                        barrier.await()
                        val answeredAt = Instant.now().plusNanos(i.toLong()) // Slight variation

                        val result = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
                            gameId = gameId,
                            questionId = questionId,
                            userId = userId,
                            answeredAt = answeredAt,
                            maxWinners = maxWinners
                        )

                        if (result.claimedWinnerSlot) {
                            successfulClaims.incrementAndGet()
                            synchronized(lock) {
                                claimedUserIds.add(userId)
                            }
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then
            assertThat(successfulClaims.get())
                .describedAs("Exactly $maxWinners users should claim winner slots")
                .isEqualTo(maxWinners)

            assertThat(claimedUserIds)
                .describedAs("Each winner should be a unique user")
                .hasSize(maxWinners)
        }

        @Test
        @DisplayName("Same user should not claim winner slot twice")
        fun `same user submitting twice should only claim once`() {
            // Given
            val gameId = UUID.randomUUID()
            val questionId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val maxWinners = 1
            val concurrentSubmissions = 20
            val barrier = CyclicBarrier(concurrentSubmissions)
            val completionLatch = CountDownLatch(concurrentSubmissions)
            val executor = Executors.newFixedThreadPool(concurrentSubmissions)

            val successfulClaims = AtomicInteger(0)

            // When - same user submits multiple times simultaneously
            (1..concurrentSubmissions).forEach { i ->
                executor.submit {
                    try {
                        barrier.await()
                        val answeredAt = Instant.now().plusNanos(i.toLong())

                        val result = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
                            gameId = gameId,
                            questionId = questionId,
                            userId = userId, // Same user
                            answeredAt = answeredAt,
                            maxWinners = maxWinners
                        )

                        if (result.claimedWinnerSlot) {
                            successfulClaims.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - user should only claim once due to SADD idempotency
            assertThat(successfulClaims.get())
                .describedAs("Same user should only claim winner slot once")
                .isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("2. Budget Race Condition Tests")
    inner class BudgetRaceConditionTests {

        /**
         * Tests that concurrent budget deductions do not over-allocate.
         *
         * Without atomic deduction, the race condition would be:
         * Instance A: SELECT remaining_budget -> $100
         * Instance B: SELECT remaining_budget -> $100
         * Instance A: UPDATE SET remaining_budget = $100 - $50 = $50
         * Instance B: UPDATE SET remaining_budget = $100 - $50 = $50
         * Result: $100 awarded but budget shows $50 remaining
         */
        @Test
        @DisplayName("Should not over-allocate budget under concurrent deductions")
        fun `concurrent budget deductions should not exceed available budget`() {
            // Given - save game in a committed transaction so it's visible to all threads
            val initialBudget = BigDecimal("100.00")
            val game = TestDataFactory.createGame(
                initialBudget = initialBudget,
                remainingBudget = initialBudget,
                status = GameStatus.ACTIVE,
                startedAt = Instant.now()
            )

            // Save in separate transaction and commit before concurrent access
            transactionTemplate.execute {
                gameRepository.save(game)
            }

            // Verify game was saved
            val savedGame = gameRepository.findById(game.id)
            assertThat(savedGame).describedAs("Game should be saved").isNotNull
            assertThat(savedGame?.remainingBudget).isEqualByComparingTo(initialBudget)

            val deductionAmount = BigDecimal("10.00")
            val concurrentRequests = 20 // Try to deduct $200 from $100 budget
            val barrier = CyclicBarrier(concurrentRequests)
            val completionLatch = CountDownLatch(concurrentRequests)
            val executor = Executors.newFixedThreadPool(concurrentRequests)

            val successfulDeductions = AtomicInteger(0)
            val failedDeductions = AtomicInteger(0)

            // When - all try to deduct simultaneously using repository directly
            // This bypasses Spring @Transactional to test raw atomic SQL operation
            (1..concurrentRequests).forEach { _ ->
                executor.submit {
                    try {
                        barrier.await()
                        // Call repository directly to test atomic SQL without Spring tx
                        val newBudget = gameRepository.deductBudgetAtomic(game.id, deductionAmount)
                        if (newBudget != null) {
                            successfulDeductions.incrementAndGet()
                        } else {
                            failedDeductions.incrementAndGet()
                        }
                    } catch (_: Exception) {
                        failedDeductions.incrementAndGet()
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - only 10 deductions should succeed (100 / 10 = 10)
            val expectedSuccessful = initialBudget.divide(deductionAmount).toInt()
            assertThat(successfulDeductions.get())
                .describedAs("Only $expectedSuccessful deductions should succeed (failed: ${failedDeductions.get()})")
                .isEqualTo(expectedSuccessful)

            // Verify remaining budget is exactly 0
            val finalBudget = gameRepository.findById(game.id)?.remainingBudget
            assertThat(finalBudget)
                .describedAs("Remaining budget should be zero")
                .isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("Should handle partial budget exhaustion correctly")
        fun `should handle partial budget exhaustion under concurrent load`() {
            // Given
            val initialBudget = BigDecimal("75.00")
            val game = TestDataFactory.createGame(
                initialBudget = initialBudget,
                remainingBudget = initialBudget,
                status = GameStatus.ACTIVE,
                startedAt = Instant.now()
            )

            // Save in separate transaction and commit before concurrent access
            transactionTemplate.execute {
                gameRepository.save(game)
            }

            val deductionAmount = BigDecimal("20.00")
            val concurrentRequests = 10 // Try to deduct $200 from $75 budget
            val barrier = CyclicBarrier(concurrentRequests)
            val completionLatch = CountDownLatch(concurrentRequests)
            val executor = Executors.newFixedThreadPool(concurrentRequests)

            val successfulDeductions = AtomicInteger(0)

            // When - use repository directly
            (1..concurrentRequests).forEach { _ ->
                executor.submit {
                    try {
                        barrier.await()
                        val newBudget = gameRepository.deductBudgetAtomic(game.id, deductionAmount)
                        if (newBudget != null) {
                            successfulDeductions.incrementAndGet()
                        }
                    } catch (_: Exception) {
                        // Expected for insufficient budget
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - only 3 deductions should succeed (75 / 20 = 3, with $15 remaining)
            assertThat(successfulDeductions.get())
                .describedAs("Only 3 deductions of $20 should succeed from $75 budget")
                .isEqualTo(3)

            // Verify remaining budget
            val finalBudget = gameRepository.findById(game.id)?.remainingBudget
            assertThat(finalBudget)
                .describedAs("Remaining budget should be $15")
                .isEqualByComparingTo(BigDecimal("15.00"))
        }
    }

    @Nested
    @DisplayName("3. Duplicate Answer Race Condition Tests")
    inner class DuplicateAnswerRaceConditionTests {

        /**
         * Tests that Cassandra LWT prevents duplicate answer submissions.
         *
         * Without LWT, the race condition would be:
         * Request A: SELECT answer WHERE user_id = X -> null (not found)
         * Request B: SELECT answer WHERE user_id = X -> null (not found)
         * Request A: INSERT answer -> success
         * Request B: INSERT answer -> success (Cassandra upsert overwrites!)
         */
        @Test
        @DisplayName("Should prevent duplicate answer submissions with Cassandra LWT")
        fun `concurrent answer submissions from same user should only succeed once`() {
            // Given
            val userId = UUID.randomUUID()
            val gameId = UUID.randomUUID()
            val questionId = UUID.randomUUID()
            val selectedOptionId = UUID.randomUUID()

            val concurrentSubmissions = 30
            val barrier = CyclicBarrier(concurrentSubmissions)
            val completionLatch = CountDownLatch(concurrentSubmissions)
            val executor = Executors.newFixedThreadPool(concurrentSubmissions)

            val successfulInserts = AtomicInteger(0)
            val duplicateRejections = AtomicInteger(0)

            // When - same user submits multiple times simultaneously
            (1..concurrentSubmissions).forEach { _ ->
                executor.submit {
                    try {
                        barrier.await()
                        val turnId = UUID.randomUUID()
                        val answeredAt = Instant.now()

                        val inserted = userQuestionAnswerRepository.insertIfNotExists(
                            userId = userId,
                            gameId = gameId,
                            questionId = questionId,
                            turnId = turnId,
                            selectedOptionId = selectedOptionId,
                            isCorrect = true,
                            rewardAmount = BigDecimal("100.00"),
                            answeredAt = answeredAt
                        )

                        if (inserted) {
                            successfulInserts.incrementAndGet()
                        } else {
                            duplicateRejections.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - exactly one insert should succeed
            assertThat(successfulInserts.get())
                .describedAs("Exactly one insert should succeed due to LWT")
                .isEqualTo(1)

            assertThat(duplicateRejections.get())
                .describedAs("All other inserts should be rejected as duplicates")
                .isEqualTo(concurrentSubmissions - 1)
        }

        @Test
        @DisplayName("Different users submitting to same question should all succeed")
        fun `different users can submit answers to same question concurrently`() {
            // Given
            val gameId = UUID.randomUUID()
            val questionId = UUID.randomUUID()

            val concurrentUsers = 20
            val barrier = CyclicBarrier(concurrentUsers)
            val completionLatch = CountDownLatch(concurrentUsers)
            val executor = Executors.newFixedThreadPool(concurrentUsers)

            val successfulInserts = AtomicInteger(0)

            // When - different users submit simultaneously
            (1..concurrentUsers).forEach { _ ->
                val userId = UUID.randomUUID() // Different user each time
                val selectedOptionId = UUID.randomUUID()
                executor.submit {
                    try {
                        barrier.await()
                        val turnId = UUID.randomUUID()
                        val answeredAt = Instant.now()

                        val inserted = userQuestionAnswerRepository.insertIfNotExists(
                            userId = userId,
                            gameId = gameId,
                            questionId = questionId,
                            turnId = turnId,
                            selectedOptionId = selectedOptionId,
                            isCorrect = true,
                            rewardAmount = BigDecimal("100.00"),
                            answeredAt = answeredAt
                        )

                        if (inserted) {
                            successfulInserts.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - all users should successfully submit
            assertThat(successfulInserts.get())
                .describedAs("All different users should be able to submit answers")
                .isEqualTo(concurrentUsers)
        }
    }

    @Nested
    @DisplayName("4. Game Status Transition Race Condition Tests")
    inner class GameStatusTransitionRaceConditionTests {

        /**
         * Tests that only one instance can transition game status.
         *
         * Without atomic transition, the race condition would be:
         * Instance A: SELECT status = DRAFT -> UPDATE status = ACTIVE
         * Instance B: SELECT status = DRAFT -> UPDATE status = ACTIVE
         * Both succeed, no conflict detection
         */
        @Test
        @DisplayName("Should allow only one transition from DRAFT to ACTIVE")
        fun `concurrent game start attempts should only succeed once`() {
            // Given
            val game = TestDataFactory.createGame(status = GameStatus.DRAFT)
            transactionTemplate.execute {
                gameRepository.save(game)
            }

            val concurrentAttempts = 20
            val barrier = CyclicBarrier(concurrentAttempts)
            val completionLatch = CountDownLatch(concurrentAttempts)
            val executor = Executors.newFixedThreadPool(concurrentAttempts)

            val successfulTransitions = AtomicInteger(0)

            // When - multiple instances try to start the same game
            (1..concurrentAttempts).forEach { _ ->
                executor.submit {
                    try {
                        barrier.await()
                        val success = gameRepository.transitionStatus(
                            id = game.id,
                            expectedStatus = GameStatus.DRAFT,
                            newStatus = GameStatus.ACTIVE,
                            timestamp = Instant.now()
                        )
                        if (success) {
                            successfulTransitions.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - exactly one transition should succeed
            assertThat(successfulTransitions.get())
                .describedAs("Only one status transition should succeed")
                .isEqualTo(1)

            // Verify game is in ACTIVE state
            val updatedGame = gameRepository.findById(game.id)
            assertThat(updatedGame?.status)
                .describedAs("Game should be in ACTIVE state")
                .isEqualTo(GameStatus.ACTIVE)
        }

        @Test
        @DisplayName("Should allow only one transition from ACTIVE to COMPLETED")
        fun `concurrent game complete attempts should only succeed once`() {
            // Given
            val game = TestDataFactory.createGame(
                status = GameStatus.ACTIVE,
                startedAt = Instant.now().minusSeconds(60)
            )
            transactionTemplate.execute {
                gameRepository.save(game)
            }

            val concurrentAttempts = 15
            val barrier = CyclicBarrier(concurrentAttempts)
            val completionLatch = CountDownLatch(concurrentAttempts)
            val executor = Executors.newFixedThreadPool(concurrentAttempts)

            val successfulTransitions = AtomicInteger(0)

            // When
            (1..concurrentAttempts).forEach { _ ->
                executor.submit {
                    try {
                        barrier.await()
                        val success = gameRepository.transitionStatus(
                            id = game.id,
                            expectedStatus = GameStatus.ACTIVE,
                            newStatus = GameStatus.COMPLETED,
                            timestamp = Instant.now()
                        )
                        if (success) {
                            successfulTransitions.incrementAndGet()
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then
            assertThat(successfulTransitions.get())
                .describedAs("Only one status transition should succeed")
                .isEqualTo(1)

            val updatedGame = gameRepository.findById(game.id)
            assertThat(updatedGame?.status)
                .describedAs("Game should be in COMPLETED state")
                .isEqualTo(GameStatus.COMPLETED)
        }
    }

    @Nested
    @DisplayName("5. User Total Reward Race Condition Tests")
    inner class UserTotalRewardRaceConditionTests {

        /**
         * Tests that Redis INCRBYFLOAT atomically updates user total rewards.
         *
         * Without atomic increment, the race condition would be:
         * Request A: Read user_total = $100 -> Answer Q1: award $50 -> Write $150
         * Request B: Read user_total = $100 -> Answer Q2: award $30 -> Write $130
         * Result: Only $130 stored, $50 reward lost
         */
        @Test
        @DisplayName("Should correctly accumulate rewards under concurrent updates")
        fun `concurrent reward increments should correctly accumulate`() {
            // Given
            val gameId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val rewardPerQuestion = BigDecimal("10.00")
            val concurrentRewards = 50
            val barrier = CyclicBarrier(concurrentRewards)
            val completionLatch = CountDownLatch(concurrentRewards)
            val executor = Executors.newFixedThreadPool(concurrentRewards)

            // When - multiple rewards applied simultaneously
            (1..concurrentRewards).forEach { i ->
                executor.submit {
                    try {
                        barrier.await()
                        redisLeaderboardService.incrementUserTotalReward(
                            gameId = gameId,
                            userId = userId,
                            rewardIncrement = rewardPerQuestion,
                            timestamp = Instant.now().plusNanos(i.toLong())
                        )
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - total should be exactly (rewardPerQuestion * concurrentRewards)
            val expectedTotal = rewardPerQuestion.multiply(BigDecimal(concurrentRewards))

            // Verify via Redis key - parse value handling both String and Number types
            val totalRewardKey = "user_total_reward:$gameId:$userId"
            val storedValue = redisTemplate.opsForValue().get(totalRewardKey)
            val storedTotal = parseRedisValue(storedValue)

            assertThat(BigDecimal.valueOf(storedTotal).setScale(2, RoundingMode.HALF_UP))
                .describedAs("Total rewards should be exactly $expectedTotal")
                .isEqualByComparingTo(expectedTotal)
        }

        @Test
        @DisplayName("Should handle varying reward amounts correctly")
        fun `concurrent varying reward increments should correctly accumulate`() {
            // Given
            val gameId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val rewards = listOf(
                BigDecimal("100.00"),
                BigDecimal("50.00"),
                BigDecimal("25.00"),
                BigDecimal("10.00"),
                BigDecimal("5.00")
            )
            val expectedTotal = rewards.reduce { acc, value -> acc.add(value) }

            val concurrentRewards = rewards.size
            val barrier = CyclicBarrier(concurrentRewards)
            val completionLatch = CountDownLatch(concurrentRewards)
            val executor = Executors.newFixedThreadPool(concurrentRewards)

            // When
            rewards.forEachIndexed { index, reward ->
                executor.submit {
                    try {
                        barrier.await()
                        redisLeaderboardService.incrementUserTotalReward(
                            gameId = gameId,
                            userId = userId,
                            rewardIncrement = reward,
                            timestamp = Instant.now().plusNanos(index.toLong())
                        )
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then
            val totalRewardKey = "user_total_reward:$gameId:$userId"
            val storedValue = redisTemplate.opsForValue().get(totalRewardKey)
            val storedTotal = parseRedisValue(storedValue)

            assertThat(BigDecimal.valueOf(storedTotal).setScale(2, RoundingMode.HALF_UP))
                .describedAs("Total rewards should be exactly $expectedTotal")
                .isEqualByComparingTo(expectedTotal)
        }

        @Test
        @DisplayName("Multiple users should have independent reward totals")
        fun `concurrent rewards for different users should be independent`() {
            // Given
            val gameId = UUID.randomUUID()
            val users = (1..5).map { UUID.randomUUID() }
            val rewardsPerUser = 20
            val rewardAmount = BigDecimal("10.00")
            val totalSubmissions = users.size * rewardsPerUser
            val barrier = CyclicBarrier(totalSubmissions)
            val completionLatch = CountDownLatch(totalSubmissions)
            val executor = Executors.newFixedThreadPool(totalSubmissions)

            // When
            users.forEach { userId ->
                (1..rewardsPerUser).forEach { i ->
                    executor.submit {
                        try {
                            barrier.await()
                            redisLeaderboardService.incrementUserTotalReward(
                                gameId = gameId,
                                userId = userId,
                                rewardIncrement = rewardAmount,
                                timestamp = Instant.now().plusNanos(i.toLong())
                            )
                        } finally {
                            completionLatch.countDown()
                        }
                    }
                }
            }

            completionLatch.await(60, TimeUnit.SECONDS)
            executor.shutdown()

            // Then - each user should have exactly (rewardAmount * rewardsPerUser)
            val expectedPerUser = rewardAmount.multiply(BigDecimal(rewardsPerUser))

            users.forEach { userId ->
                val totalRewardKey = "user_total_reward:$gameId:$userId"
                val storedValue = redisTemplate.opsForValue().get(totalRewardKey)
                val storedTotal = parseRedisValue(storedValue)

                assertThat(BigDecimal.valueOf(storedTotal).setScale(2, RoundingMode.HALF_UP))
                    .describedAs("User $userId should have exactly $expectedPerUser")
                    .isEqualByComparingTo(expectedPerUser)
            }
        }
    }

    @Nested
    @DisplayName("Combined Race Condition Scenarios")
    inner class CombinedRaceConditionTests {

        /**
         * Simulates a realistic scenario where multiple components are accessed concurrently.
         */
        @Test
        @DisplayName("Should handle combined leaderboard and budget operations under load")
        fun `combined operations should maintain consistency`() {
            // Given
            val initialBudget = BigDecimal("500.00")
            val game = TestDataFactory.createGame(
                initialBudget = initialBudget,
                remainingBudget = initialBudget,
                status = GameStatus.ACTIVE,
                startedAt = Instant.now()
            )
            transactionTemplate.execute {
                gameRepository.save(game)
            }

            val questionId = UUID.randomUUID()
            val rewardPerWinner = BigDecimal("100.00")
            val maxWinners = 1
            val concurrentUsers = 50
            val barrier = CyclicBarrier(concurrentUsers)
            val completionLatch = CountDownLatch(concurrentUsers)
            val executor = Executors.newFixedThreadPool(concurrentUsers)

            val winnersAwarded = AtomicInteger(0)
            val claimedUserIds = mutableSetOf<UUID>()
            val lock = Object()

            // When - simulate 50 users answering correctly at the same time
            (1..concurrentUsers).forEach { i ->
                val userId = UUID.randomUUID()
                executor.submit {
                    try {
                        barrier.await()
                        val answeredAt = Instant.now().plusNanos(i.toLong())

                        // Step 1: Try to claim winner slot
                        val claimResult = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
                            gameId = game.id,
                            questionId = questionId,
                            userId = userId,
                            answeredAt = answeredAt,
                            maxWinners = maxWinners
                        )

                        // Step 2: Only award budget if claimed slot (using repository directly)
                        if (claimResult.claimedWinnerSlot) {
                            val newBudget = gameRepository.deductBudgetAtomic(game.id, rewardPerWinner)
                            if (newBudget != null) {
                                winnersAwarded.incrementAndGet()
                                synchronized(lock) {
                                    claimedUserIds.add(userId)
                                }
                            }
                        }
                    } finally {
                        completionLatch.countDown()
                    }
                }
            }

            completionLatch.await(30, TimeUnit.SECONDS)
            executor.shutdown()

            // Then
            assertThat(winnersAwarded.get())
                .describedAs("Exactly $maxWinners winner(s) should be awarded")
                .isEqualTo(maxWinners)

            assertThat(claimedUserIds)
                .describedAs("Winner(s) should be unique")
                .hasSize(maxWinners)

            // Verify budget was deducted correctly
            val finalBudget = gameRepository.findById(game.id)?.remainingBudget
            assertThat(finalBudget)
                .describedAs("Budget should be reduced by exactly one reward")
                .isEqualByComparingTo(initialBudget.subtract(rewardPerWinner))
        }
    }
}
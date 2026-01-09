package com.gameplatform.game.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

/**
 * Custom business metrics for the game platform.
 * All metrics are prefixed with "game.platform." for namespacing.
 */
@Component
class GameMetrics(private val registry: MeterRegistry) {

    // Answer submission metrics
    private val answerSubmissionsTotal = Counter.builder("game.platform.answer.submissions.total")
        .description("Total number of answer submissions")
        .register(registry)

    private val correctAnswersTotal = Counter.builder("game.platform.answer.correct.total")
        .description("Total number of correct answers")
        .register(registry)

    private val incorrectAnswersTotal = Counter.builder("game.platform.answer.incorrect.total")
        .description("Total number of incorrect answers")
        .register(registry)

    private val duplicateAnswersTotal = Counter.builder("game.platform.answer.duplicate.total")
        .description("Total number of duplicate answer attempts")
        .register(registry)

    private val lateAnswersTotal = Counter.builder("game.platform.answer.late.total")
        .description("Total number of late answer submissions (after question expired)")
        .register(registry)

    // Reward metrics
    private val rewardsDistributed = Counter.builder("game.platform.rewards.distributed.total")
        .description("Total rewards distributed in currency units")
        .register(registry)

    private val rewardsCount = Counter.builder("game.platform.rewards.count")
        .description("Total number of rewards awarded")
        .register(registry)

    // Game lifecycle metrics
    private val gamesCreated = Counter.builder("game.platform.games.created.total")
        .description("Total number of games created")
        .register(registry)

    private val gamesStarted = Counter.builder("game.platform.games.started.total")
        .description("Total number of games started")
        .register(registry)

    private val gamesCompleted = Counter.builder("game.platform.games.completed.total")
        .description("Total number of games completed")
        .register(registry)

    // Budget metrics
    private val budgetAllocated = Counter.builder("game.platform.budget.allocated.total")
        .description("Total budget allocated to games")
        .register(registry)

    private val budgetAwarded = Counter.builder("game.platform.budget.awarded.total")
        .description("Total budget awarded to users")
        .register(registry)

    // Leaderboard metrics
    private val leaderboardCalculations = Counter.builder("game.platform.leaderboard.calculations.total")
        .description("Total number of leaderboard calculations")
        .register(registry)

    // Timing metrics
    fun recordAnswerSubmissionTime(gameId: UUID, block: () -> Unit) {
        Timer.builder("game.platform.answer.submission.time")
            .description("Time taken to process answer submission")
            .tag("gameId", gameId.toString())
            .register(registry)
            .record(block)
    }

    fun recordLeaderboardCalculationTime(gameId: UUID, block: () -> Unit) {
        Timer.builder("game.platform.leaderboard.calculation.time")
            .description("Time taken to calculate leaderboard")
            .tag("gameId", gameId.toString())
            .register(registry)
            .record(block)
    }

    // Answer submission metrics
    fun recordAnswerSubmission(isCorrect: Boolean, gameId: UUID, questionId: UUID) {
        answerSubmissionsTotal.increment()
        if (isCorrect) {
            correctAnswersTotal.increment()
        } else {
            incorrectAnswersTotal.increment()
        }
    }

    fun recordDuplicateAnswer() {
        duplicateAnswersTotal.increment()
    }

    fun recordLateAnswer() {
        lateAnswersTotal.increment()
    }

    // Reward metrics
    fun recordReward(amount: BigDecimal, gameId: UUID, userId: UUID) {
        rewardsDistributed.increment(amount.toDouble())
        rewardsCount.increment()
    }

    // Game lifecycle metrics
    fun recordGameCreated(gameId: UUID) {
        gamesCreated.increment()
    }

    fun recordGameStarted(gameId: UUID) {
        gamesStarted.increment()
    }

    fun recordGameCompleted(gameId: UUID) {
        gamesCompleted.increment()
    }

    // Budget metrics
    fun recordBudgetAllocated(amount: BigDecimal, gameId: UUID) {
        budgetAllocated.increment(amount.toDouble())
    }

    fun recordBudgetAwarded(amount: BigDecimal, gameId: UUID, userId: UUID) {
        budgetAwarded.increment(amount.toDouble())
    }

    // Leaderboard metrics
    fun recordLeaderboardCalculation(gameId: UUID) {
        leaderboardCalculations.increment()
    }

    // Active games gauge (requires manual update)
    fun updateActiveGamesCount(count: Int) {
        registry.gauge("game.platform.games.active", count)
    }
}
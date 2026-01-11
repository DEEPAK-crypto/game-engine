package com.gameplatform.game.service.evaluation

import com.gameplatform.game.dto.SubmitAnswerRequest
import com.gameplatform.game.domain.model.Question
import java.math.BigDecimal
import java.time.Instant

/**
 * Strategy interface for evaluating game submissions based on game type.
 * Different game types have different rules for determining winning and awarding rewards.
 *
 * This interface supports various game mechanics:
 * - MCQ games (correct/incorrect answers)
 * - Probability-based games (slot machines, lotteries)
 * - Time-based games (fastest response)
 * - Skill-based games (scoring systems)
 */
interface AnswerEvaluator {

    /**
     * Evaluates if the submitted answer is correct.
     *
     * @param question The question/challenge being answered
     * @param request The answer submission request
     * @return true if the answer is correct, false otherwise
     */
    fun isAnswerCorrect(
        question: Question,
        request: SubmitAnswerRequest
    ): Boolean

    /**
     * Determines if a reward should be awarded and calculates the amount.
     *
     * @param question The question/challenge being answered
     * @param userRank The user's rank among correct submissions
     * @return The reward evaluation result
     */
    fun calculateReward(
        question: Question,
        userRank: Int
    ): RewardEvaluationResult

    /**
     * Returns the maximum number of winners for this game type.
     * This is used by the atomic leaderboard operation to determine
     * how many winner slots are available.
     *
     * @return The maximum number of winners that can receive rewards
     */
    fun getMaxWinners(): Int
}

/**
 * Result of reward evaluation.
 *
 * @property shouldAwardReward Whether the reward should be awarded (based on rank and game rules)
 * @property rewardAmount The calculated reward amount
 */
data class RewardEvaluationResult(
    val shouldAwardReward: Boolean,
    val rewardAmount: BigDecimal
)
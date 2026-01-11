package com.gameplatform.game.service.evaluation

import com.gameplatform.game.dto.SubmitAnswerRequest
import com.gameplatform.game.domain.model.Question
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Answer evaluator for MCQ_FIFO game type.
 *
 * Rules:
 * - The first N correct answers win rewards (currently N=1)
 * - All other correct answers receive zero reward
 * - Incorrect answers always receive zero reward
 * - Order is determined by submission timestamp (FIFO - First In, First Out)
 *
 * Future Enhancement:
 * To support multiple winners, add a `winnerCount` field to the Question or Game model
 * and update this evaluator to check `userRank <= question.winnerCount`.
 * Reward distribution could be:
 * - Equal rewards for all N winners
 * - Tiered rewards (1st gets 100%, 2nd gets 50%, etc.)
 * - Proportional distribution from question.reward pool
 */
@Component
class McqFifoAnswerEvaluator : AnswerEvaluator {

    companion object {
        // TODO: Make this configurable per question or game
        // Currently set to 1, meaning only the first correct answer wins
        // Change to N to reward the first N correct answers
        private const val WINNER_COUNT = 1
    }

    override fun isAnswerCorrect(
        question: Question,
        request: SubmitAnswerRequest
    ): Boolean {
        return question.correctOptionId == request.selectedOptionId
    }

    override fun calculateReward(
        question: Question,
        userRank: Int
    ): RewardEvaluationResult {
        // Award reward to the first N correct answers (ranks 1 through N)
        val shouldAwardReward = userRank <= WINNER_COUNT
        val rewardAmount = if (shouldAwardReward) question.reward else BigDecimal.ZERO

        return RewardEvaluationResult(
            shouldAwardReward = shouldAwardReward,
            rewardAmount = rewardAmount
        )
    }

    override fun getMaxWinners(): Int = WINNER_COUNT
}
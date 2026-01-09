package com.gameplatform.game.service.evaluation

import com.gameplatform.game.domain.enums.GameType
import org.springframework.stereotype.Component

/**
 * Factory for creating the appropriate AnswerEvaluator based on game type.
 *
 * This factory uses the Strategy pattern to provide different evaluation
 * strategies for different game types, making it easy to add new game types
 * without modifying existing code (Open/Closed Principle).
 *
 * Currently supported game types:
 * - MCQ_FIFO: Multiple choice, first correct answer wins
 *
 * Future game types could include:
 * - MCQ_FASTEST: Multiple choice, first N fastest correct answers win
 * - SLOT_MACHINE: Probability-based rewards
 * - LOTTERY: Random winner selection
 * - SKILL_BASED: Score-based rewards
 */
@Component
class AnswerEvaluatorFactory(
    private val mcqFifoAnswerEvaluator: McqFifoAnswerEvaluator
) {

    /**
     * Gets the appropriate answer evaluator for the given game type.
     *
     * @param gameType The type of game
     * @return The answer evaluator for that game type
     * @throws IllegalArgumentException if the game type is not supported
     */
    fun getEvaluator(gameType: GameType): AnswerEvaluator {
        return when (gameType) {
            GameType.MCQ_FIFO -> mcqFifoAnswerEvaluator
            GameType.MCQ_FASTEST -> throw IllegalArgumentException(
                "Game type MCQ_FASTEST is not yet implemented. Use MCQ_FIFO instead."
            )
        }
    }
}
package com.gameplatform.game.service

import java.math.BigDecimal
import java.util.UUID

interface BudgetService {
    /**
     * Allocate budget for a question reward (called when question becomes active).
     * Uses SERIALIZABLE transaction isolation to prevent over-allocation.
     */
    fun allocateQuestionReward(gameId: UUID, questionId: UUID, amount: BigDecimal): Boolean

    /**
     * Award budget to a user (called when user correctly answers).
     * Uses SERIALIZABLE transaction isolation.
     */
    fun awardToUser(gameId: UUID, userId: UUID, questionId: UUID, amount: BigDecimal)

    /**
     * Get remaining budget for a game.
     */
    fun getRemainingBudget(gameId: UUID): BigDecimal
}
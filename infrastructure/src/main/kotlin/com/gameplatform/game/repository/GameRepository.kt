package com.gameplatform.game.repository

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.model.Game
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface GameRepository {
    fun save(game: Game): Game
    fun findById(id: UUID): Game?
    fun findAll(): List<Game>
    fun findByStatus(status: GameStatus): List<Game>
    fun updateStatus(id: UUID, status: GameStatus, timestamp: Instant): Boolean
    fun updateRemainingBudget(id: UUID, newBudget: BigDecimal): Boolean
    fun delete(id: UUID): Boolean

    /**
     * Atomically transitions game status from expected state to new state.
     *
     * This operation is safe for concurrent access across multiple instances.
     * The WHERE clause checks both ID and expected current status, preventing
     * race conditions where two instances try to transition simultaneously.
     *
     * @param id The game ID
     * @param expectedStatus The current status the game must be in
     * @param newStatus The status to transition to
     * @param timestamp The timestamp for the transition
     * @return true if transition succeeded, false if game not found or not in expected status
     */
    fun transitionStatus(
        id: UUID,
        expectedStatus: GameStatus,
        newStatus: GameStatus,
        timestamp: Instant
    ): Boolean

    /**
     * Atomically deducts an amount from the game's remaining budget.
     *
     * This operation is safe for concurrent access across multiple instances.
     * It uses a single UPDATE statement with a WHERE clause that checks
     * the budget is sufficient, preventing race conditions.
     *
     * @param id The game ID
     * @param amount The amount to deduct
     * @return The new remaining budget if successful, null if insufficient funds or game not found
     */
    fun deductBudgetAtomic(id: UUID, amount: BigDecimal): BigDecimal?
}
package com.gameplatform.game.domain.model

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Game(
    val id: UUID,
    val name: String,
    val gameType: GameType,
    val initialBudget: BigDecimal,
    val remainingBudget: BigDecimal,
    val status: GameStatus,
    val scheduledAt: Instant?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val questionTimerSeconds: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun isActive(): Boolean = status == GameStatus.ACTIVE

    fun isScheduled(): Boolean = status == GameStatus.SCHEDULED

    fun isCompleted(): Boolean = status == GameStatus.COMPLETED

    fun canStart(): Boolean = isScheduled() && scheduledAt != null && Instant.now() >= scheduledAt

    fun hasBudgetFor(amount: BigDecimal): Boolean = remainingBudget >= amount
}

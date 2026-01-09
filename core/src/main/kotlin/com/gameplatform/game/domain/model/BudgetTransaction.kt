package com.gameplatform.game.domain.model

import com.gameplatform.game.domain.enums.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class BudgetTransaction(
    val id: UUID,
    val gameId: UUID,
    val amount: BigDecimal,
    val remainingBudget: BigDecimal,
    val transactionType: TransactionType,
    val userId: UUID?,
    val questionId: UUID?,
    val description: String?,
    val createdAt: Instant
)

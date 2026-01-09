package com.gameplatform.game.repository

import com.gameplatform.game.domain.enums.TransactionType
import com.gameplatform.game.domain.model.BudgetTransaction
import java.util.UUID

interface BudgetTransactionRepository {
    fun save(transaction: BudgetTransaction): BudgetTransaction
    fun findById(id: UUID): BudgetTransaction?
    fun findByGameId(gameId: UUID): List<BudgetTransaction>
    fun findByGameIdAndType(gameId: UUID, type: TransactionType): List<BudgetTransaction>
    fun findByGameIdOrderByCreatedAt(gameId: UUID): List<BudgetTransaction>
}
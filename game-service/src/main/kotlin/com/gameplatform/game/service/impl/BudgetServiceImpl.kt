package com.gameplatform.game.service.impl

import com.gameplatform.game.domain.enums.TransactionType
import com.gameplatform.game.domain.model.BudgetTransaction
import com.gameplatform.game.exception.BudgetAllocationException
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InsufficientBudgetException
import com.gameplatform.game.repository.BudgetTransactionRepository
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.BudgetService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class BudgetServiceImpl(
    private val gameRepository: GameRepository,
    private val budgetTransactionRepository: BudgetTransactionRepository
) : BudgetService {

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun allocateQuestionReward(gameId: UUID, questionId: UUID, amount: BigDecimal): Boolean {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        if (game.remainingBudget < amount) {
            return false
        }

        val newBudget = game.remainingBudget - amount
        val success = gameRepository.updateRemainingBudget(gameId, newBudget)

        if (!success) {
            throw BudgetAllocationException("Failed to allocate budget for question $questionId")
        }

        val transaction = BudgetTransaction(
            id = UUID.randomUUID(),
            gameId = gameId,
            amount = amount,
            remainingBudget = newBudget,
            transactionType = TransactionType.ALLOCATION,
            userId = null,
            questionId = questionId,
            description = "Question reward allocation",
            createdAt = Instant.now()
        )

        budgetTransactionRepository.save(transaction)
        return true
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun awardToUser(gameId: UUID, userId: UUID, questionId: UUID, amount: BigDecimal) {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        if (game.remainingBudget < amount) {
            throw InsufficientBudgetException(
                gameId,
                amount.toString(),
                game.remainingBudget.toString()
            )
        }

        val newBudget = game.remainingBudget - amount
        val success = gameRepository.updateRemainingBudget(gameId, newBudget)

        if (!success) {
            throw BudgetAllocationException("Failed to award budget to user $userId")
        }

        val transaction = BudgetTransaction(
            id = UUID.randomUUID(),
            gameId = gameId,
            amount = amount,
            remainingBudget = newBudget,
            transactionType = TransactionType.REWARD,
            userId = userId,
            questionId = questionId,
            description = "Reward to user for correct answer",
            createdAt = Instant.now()
        )

        budgetTransactionRepository.save(transaction)
    }

    @Transactional(readOnly = true)
    override fun getRemainingBudget(gameId: UUID): BigDecimal {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)
        return game.remainingBudget
    }
}
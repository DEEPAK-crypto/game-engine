package com.gameplatform.game.service.impl

import com.gameplatform.game.domain.enums.TransactionType
import com.gameplatform.game.domain.model.BudgetTransaction
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InsufficientBudgetException
import com.gameplatform.game.metrics.GameMetrics
import com.gameplatform.game.repository.BudgetTransactionRepository
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.BudgetService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class BudgetServiceImpl(
    private val gameRepository: GameRepository,
    private val budgetTransactionRepository: BudgetTransactionRepository,
    private val gameMetrics: GameMetrics
) : BudgetService {

    private val log = LoggerFactory.getLogger(BudgetServiceImpl::class.java)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun allocateQuestionReward(gameId: UUID, questionId: UUID, amount: BigDecimal): Boolean {
        log.info(
            "Allocating question reward",
            kv("gameId", gameId),
            kv("questionId", questionId),
            kv("amount", amount)
        )

        // Atomic deduction - single UPDATE with WHERE clause checking budget
        // This prevents race conditions in multi-instance deployments
        val newBudget = gameRepository.deductBudgetAtomic(gameId, amount)

        if (newBudget == null) {
            // Either game not found or insufficient budget
            val game = gameRepository.findById(gameId)
            if (game == null) {
                log.warn("Game not found for budget allocation", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }
            log.warn(
                "Insufficient budget for question reward allocation (atomic check failed)",
                kv("gameId", gameId),
                kv("questionId", questionId),
                kv("requestedAmount", amount),
                kv("remainingBudget", game.remainingBudget)
            )
            return false
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

        log.info(
            "Question reward allocated successfully (atomic)",
            kv("gameId", gameId),
            kv("questionId", questionId),
            kv("amount", amount),
            kv("newRemainingBudget", newBudget)
        )

        gameMetrics.recordBudgetAllocated(amount, gameId)

        return true
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun awardToUser(gameId: UUID, userId: UUID, questionId: UUID, amount: BigDecimal) {
        log.info(
            "Awarding budget to user",
            kv("gameId", gameId),
            kv("userId", userId),
            kv("questionId", questionId),
            kv("amount", amount)
        )

        // Atomic deduction - single UPDATE with WHERE clause checking budget
        // This prevents race conditions in multi-instance deployments
        val newBudget = gameRepository.deductBudgetAtomic(gameId, amount)

        if (newBudget == null) {
            // Either game not found or insufficient budget
            val game = gameRepository.findById(gameId)
            if (game == null) {
                log.warn("Game not found for user award", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }
            log.error(
                "Insufficient budget for user award (atomic check failed)",
                kv("gameId", gameId),
                kv("userId", userId),
                kv("requestedAmount", amount),
                kv("remainingBudget", game.remainingBudget)
            )
            throw InsufficientBudgetException(
                gameId,
                amount.toString(),
                game.remainingBudget.toString()
            )
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

        log.info(
            "Budget awarded to user successfully (atomic)",
            kv("gameId", gameId),
            kv("userId", userId),
            kv("questionId", questionId),
            kv("amount", amount),
            kv("newRemainingBudget", newBudget)
        )

        gameMetrics.recordBudgetAwarded(amount, gameId, userId)
    }

    @Transactional(readOnly = true)
    override fun getRemainingBudget(gameId: UUID): BigDecimal {
        log.debug("Retrieving remaining budget", kv("gameId", gameId))
        val game = gameRepository.findById(gameId)
            ?: run {
                log.warn("Game not found when retrieving budget", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }
        return game.remainingBudget
    }
}
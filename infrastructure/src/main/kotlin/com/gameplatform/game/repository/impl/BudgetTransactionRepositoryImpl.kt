package com.gameplatform.game.repository.impl

import com.gameplatform.game.domain.enums.TransactionType
import com.gameplatform.game.domain.model.BudgetTransaction
import com.gameplatform.game.jooq.tables.references.BUDGET_TRANSACTIONS
import com.gameplatform.game.repository.BudgetTransactionRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class BudgetTransactionRepositoryImpl(private val dsl: DSLContext) : BudgetTransactionRepository {

    override fun save(transaction: BudgetTransaction): BudgetTransaction {
        dsl.insertInto(BUDGET_TRANSACTIONS)
            .set(BUDGET_TRANSACTIONS.ID, transaction.id)
            .set(BUDGET_TRANSACTIONS.GAME_ID, transaction.gameId)
            .set(BUDGET_TRANSACTIONS.AMOUNT, transaction.amount)
            .set(BUDGET_TRANSACTIONS.REMAINING_BUDGET, transaction.remainingBudget)
            .set(BUDGET_TRANSACTIONS.TRANSACTION_TYPE, transaction.transactionType.name)
            .set(BUDGET_TRANSACTIONS.USER_ID, transaction.userId)
            .set(BUDGET_TRANSACTIONS.QUESTION_ID, transaction.questionId)
            .set(BUDGET_TRANSACTIONS.DESCRIPTION, transaction.description)
            .set(BUDGET_TRANSACTIONS.CREATED_AT, transaction.createdAt.toLocalDateTime())
            .execute()

        return transaction
    }

    override fun findById(id: UUID): BudgetTransaction? {
        return dsl.selectFrom(BUDGET_TRANSACTIONS)
            .where(BUDGET_TRANSACTIONS.ID.eq(id))
            .fetchOne()
            ?.let { mapToBudgetTransaction(it) }
    }

    override fun findByGameId(gameId: UUID): List<BudgetTransaction> {
        return dsl.selectFrom(BUDGET_TRANSACTIONS)
            .where(BUDGET_TRANSACTIONS.GAME_ID.eq(gameId))
            .fetch()
            .map { mapToBudgetTransaction(it) }
    }

    override fun findByGameIdAndType(gameId: UUID, type: TransactionType): List<BudgetTransaction> {
        return dsl.selectFrom(BUDGET_TRANSACTIONS)
            .where(BUDGET_TRANSACTIONS.GAME_ID.eq(gameId))
            .and(BUDGET_TRANSACTIONS.TRANSACTION_TYPE.eq(type.name))
            .fetch()
            .map { mapToBudgetTransaction(it) }
    }

    override fun findByGameIdOrderByCreatedAt(gameId: UUID): List<BudgetTransaction> {
        return dsl.selectFrom(BUDGET_TRANSACTIONS)
            .where(BUDGET_TRANSACTIONS.GAME_ID.eq(gameId))
            .orderBy(BUDGET_TRANSACTIONS.CREATED_AT.asc())
            .fetch()
            .map { mapToBudgetTransaction(it) }
    }

    private fun mapToBudgetTransaction(record: org.jooq.Record): BudgetTransaction {
        return BudgetTransaction(
            id = record.get(BUDGET_TRANSACTIONS.ID)!!,
            gameId = record.get(BUDGET_TRANSACTIONS.GAME_ID)!!,
            amount = record.get(BUDGET_TRANSACTIONS.AMOUNT)!!,
            remainingBudget = record.get(BUDGET_TRANSACTIONS.REMAINING_BUDGET)!!,
            transactionType = TransactionType.valueOf(record.get(BUDGET_TRANSACTIONS.TRANSACTION_TYPE)!!),
            userId = record.get(BUDGET_TRANSACTIONS.USER_ID),
            questionId = record.get(BUDGET_TRANSACTIONS.QUESTION_ID),
            description = record.get(BUDGET_TRANSACTIONS.DESCRIPTION),
            createdAt = record.get(BUDGET_TRANSACTIONS.CREATED_AT)!!.toInstant()
        )
    }

    private fun Instant.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun LocalDateTime.toInstant(): Instant =
        this.toInstant(ZoneOffset.UTC)
}
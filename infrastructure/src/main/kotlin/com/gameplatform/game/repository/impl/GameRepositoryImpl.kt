package com.gameplatform.game.repository.impl

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.jooq.tables.references.GAMES
import com.gameplatform.game.repository.GameRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class GameRepositoryImpl(private val dsl: DSLContext) : GameRepository {

    override fun save(game: Game): Game {
        dsl.insertInto(GAMES)
            .set(GAMES.ID, game.id)
            .set(GAMES.NAME, game.name)
            .set(GAMES.GAME_TYPE, game.gameType.name)
            .set(GAMES.INITIAL_BUDGET, game.initialBudget)
            .set(GAMES.REMAINING_BUDGET, game.remainingBudget)
            .set(GAMES.STATUS, game.status.name)
            .set(GAMES.SCHEDULED_AT, game.scheduledAt?.toLocalDateTime())
            .set(GAMES.STARTED_AT, game.startedAt?.toLocalDateTime())
            .set(GAMES.ENDED_AT, game.endedAt?.toLocalDateTime())
            .set(GAMES.QUESTION_TIMER_SECONDS, game.questionTimerSeconds)
            .set(GAMES.CREATED_AT, game.createdAt.toLocalDateTime())
            .set(GAMES.UPDATED_AT, game.updatedAt.toLocalDateTime())
            .execute()

        return game
    }

    override fun findById(id: UUID): Game? {
        return dsl.selectFrom(GAMES)
            .where(GAMES.ID.eq(id))
            .fetchOne()
            ?.let { mapToGame(it) }
    }

    override fun findAll(): List<Game> {
        return dsl.selectFrom(GAMES)
            .fetch()
            .map { mapToGame(it) }
    }

    override fun findByStatus(status: GameStatus): List<Game> {
        return dsl.selectFrom(GAMES)
            .where(GAMES.STATUS.eq(status.name))
            .fetch()
            .map { mapToGame(it) }
    }

    override fun updateStatus(id: UUID, status: GameStatus, timestamp: Instant): Boolean {
        val updateStep = dsl.update(GAMES)
            .set(GAMES.STATUS, status.name)
            .set(GAMES.UPDATED_AT, Instant.now().toLocalDateTime())

        val withTimestamp = when (status) {
            GameStatus.ACTIVE -> updateStep.set(GAMES.STARTED_AT, timestamp.toLocalDateTime())
            GameStatus.COMPLETED -> updateStep.set(GAMES.ENDED_AT, timestamp.toLocalDateTime())
            else -> updateStep
        }

        return withTimestamp
            .where(GAMES.ID.eq(id))
            .execute() > 0
    }

    override fun updateRemainingBudget(id: UUID, newBudget: BigDecimal): Boolean {
        val updated = dsl.update(GAMES)
            .set(GAMES.REMAINING_BUDGET, newBudget)
            .set(GAMES.UPDATED_AT, Instant.now().toLocalDateTime())
            .where(GAMES.ID.eq(id))
            .execute()

        return updated > 0
    }

    override fun delete(id: UUID): Boolean {
        val deleted = dsl.deleteFrom(GAMES)
            .where(GAMES.ID.eq(id))
            .execute()

        return deleted > 0
    }

    private fun mapToGame(record: org.jooq.Record): Game {
        return Game(
            id = record.get(GAMES.ID)!!,
            name = record.get(GAMES.NAME)!!,
            gameType = GameType.valueOf(record.get(GAMES.GAME_TYPE)!!),
            initialBudget = record.get(GAMES.INITIAL_BUDGET)!!,
            remainingBudget = record.get(GAMES.REMAINING_BUDGET)!!,
            status = GameStatus.valueOf(record.get(GAMES.STATUS)!!),
            scheduledAt = record.get(GAMES.SCHEDULED_AT)?.toInstant(),
            startedAt = record.get(GAMES.STARTED_AT)?.toInstant(),
            endedAt = record.get(GAMES.ENDED_AT)?.toInstant(),
            questionTimerSeconds = record.get(GAMES.QUESTION_TIMER_SECONDS)!!,
            createdAt = record.get(GAMES.CREATED_AT)!!.toInstant(),
            updatedAt = record.get(GAMES.UPDATED_AT)!!.toInstant()
        )
    }

    private fun Instant.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun LocalDateTime.toInstant(): Instant =
        this.toInstant(ZoneOffset.UTC)
}
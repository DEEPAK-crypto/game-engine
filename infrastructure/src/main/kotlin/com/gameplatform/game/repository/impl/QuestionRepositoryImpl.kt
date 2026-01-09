package com.gameplatform.game.repository.impl

import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.jooq.tables.references.QUESTIONS
import com.gameplatform.game.repository.QuestionRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class QuestionRepositoryImpl(private val dsl: DSLContext) : QuestionRepository {

    override fun save(question: Question): Question {
        dsl.insertInto(QUESTIONS)
            .set(QUESTIONS.ID, question.id)
            .set(QUESTIONS.GAME_ID, question.gameId)
            .set(QUESTIONS.QUESTION_TEXT, question.questionText)
            .set(QUESTIONS.ORDER_INDEX, question.orderIndex)
            .set(QUESTIONS.CORRECT_OPTION_ID, question.correctOptionId)
            .set(QUESTIONS.REWARD, question.reward)
            .set(QUESTIONS.DURATION_SECONDS, question.durationSeconds)
            .set(QUESTIONS.CREATED_AT, question.createdAt.toLocalDateTime())
            .execute()

        return question
    }

    override fun saveAll(questions: List<Question>): List<Question> {
        val batch = dsl.batch(
            questions.map { question ->
                dsl.insertInto(QUESTIONS)
                    .set(QUESTIONS.ID, question.id)
                    .set(QUESTIONS.GAME_ID, question.gameId)
                    .set(QUESTIONS.QUESTION_TEXT, question.questionText)
                    .set(QUESTIONS.ORDER_INDEX, question.orderIndex)
                    .set(QUESTIONS.CORRECT_OPTION_ID, question.correctOptionId)
                    .set(QUESTIONS.REWARD, question.reward)
                    .set(QUESTIONS.DURATION_SECONDS, question.durationSeconds)
                    .set(QUESTIONS.CREATED_AT, question.createdAt.toLocalDateTime())
            }
        )
        batch.execute()
        return questions
    }

    override fun findById(id: UUID): Question? {
        return dsl.selectFrom(QUESTIONS)
            .where(QUESTIONS.ID.eq(id))
            .fetchOne()
            ?.let { mapToQuestion(it) }
    }

    override fun findByGameId(gameId: UUID): List<Question> {
        return dsl.selectFrom(QUESTIONS)
            .where(QUESTIONS.GAME_ID.eq(gameId))
            .fetch()
            .map { mapToQuestion(it) }
    }

    override fun findByGameIdOrderByIndex(gameId: UUID): List<Question> {
        return dsl.selectFrom(QUESTIONS)
            .where(QUESTIONS.GAME_ID.eq(gameId))
            .orderBy(QUESTIONS.ORDER_INDEX.asc())
            .fetch()
            .map { mapToQuestion(it) }
    }

    override fun updateCorrectOption(id: UUID, correctOptionId: UUID): Boolean {
        val updated = dsl.update(QUESTIONS)
            .set(QUESTIONS.CORRECT_OPTION_ID, correctOptionId)
            .where(QUESTIONS.ID.eq(id))
            .execute()

        return updated > 0
    }

    override fun delete(id: UUID): Boolean {
        val deleted = dsl.deleteFrom(QUESTIONS)
            .where(QUESTIONS.ID.eq(id))
            .execute()

        return deleted > 0
    }

    override fun deleteByGameId(gameId: UUID): Int {
        return dsl.deleteFrom(QUESTIONS)
            .where(QUESTIONS.GAME_ID.eq(gameId))
            .execute()
    }

    private fun mapToQuestion(record: org.jooq.Record): Question {
        return Question(
            id = record.get(QUESTIONS.ID)!!,
            gameId = record.get(QUESTIONS.GAME_ID)!!,
            questionText = record.get(QUESTIONS.QUESTION_TEXT)!!,
            orderIndex = record.get(QUESTIONS.ORDER_INDEX)!!,
            correctOptionId = record.get(QUESTIONS.CORRECT_OPTION_ID),
            reward = record.get(QUESTIONS.REWARD)!!,
            durationSeconds = record.get(QUESTIONS.DURATION_SECONDS)!!,
            createdAt = record.get(QUESTIONS.CREATED_AT)!!.toInstant()
        )
    }

    private fun Instant.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun LocalDateTime.toInstant(): Instant =
        this.toInstant(ZoneOffset.UTC)
}

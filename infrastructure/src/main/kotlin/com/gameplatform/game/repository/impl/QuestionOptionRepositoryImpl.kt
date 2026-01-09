package com.gameplatform.game.repository.impl

import com.gameplatform.game.domain.model.QuestionOption
import com.gameplatform.game.jooq.tables.references.QUESTION_OPTIONS
import com.gameplatform.game.repository.QuestionOptionRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class QuestionOptionRepositoryImpl(private val dsl: DSLContext) : QuestionOptionRepository {

    override fun save(option: QuestionOption): QuestionOption {
        dsl.insertInto(QUESTION_OPTIONS)
            .set(QUESTION_OPTIONS.ID, option.id)
            .set(QUESTION_OPTIONS.QUESTION_ID, option.questionId)
            .set(QUESTION_OPTIONS.OPTION_TEXT, option.optionText)
            .set(QUESTION_OPTIONS.ORDER_INDEX, option.orderIndex)
            .set(QUESTION_OPTIONS.CREATED_AT, option.createdAt.toLocalDateTime())
            .execute()

        return option
    }

    override fun saveAll(options: List<QuestionOption>): List<QuestionOption> {
        val batch = dsl.batch(
            options.map { option ->
                dsl.insertInto(QUESTION_OPTIONS)
                    .set(QUESTION_OPTIONS.ID, option.id)
                    .set(QUESTION_OPTIONS.QUESTION_ID, option.questionId)
                    .set(QUESTION_OPTIONS.OPTION_TEXT, option.optionText)
                    .set(QUESTION_OPTIONS.ORDER_INDEX, option.orderIndex)
                    .set(QUESTION_OPTIONS.CREATED_AT, option.createdAt.toLocalDateTime())
            }
        )
        batch.execute()
        return options
    }

    override fun findById(id: UUID): QuestionOption? {
        return dsl.selectFrom(QUESTION_OPTIONS)
            .where(QUESTION_OPTIONS.ID.eq(id))
            .fetchOne()
            ?.let { mapToQuestionOption(it) }
    }

    override fun findByQuestionId(questionId: UUID): List<QuestionOption> {
        return dsl.selectFrom(QUESTION_OPTIONS)
            .where(QUESTION_OPTIONS.QUESTION_ID.eq(questionId))
            .fetch()
            .map { mapToQuestionOption(it) }
    }

    override fun findByQuestionIdOrderByIndex(questionId: UUID): List<QuestionOption> {
        return dsl.selectFrom(QUESTION_OPTIONS)
            .where(QUESTION_OPTIONS.QUESTION_ID.eq(questionId))
            .orderBy(QUESTION_OPTIONS.ORDER_INDEX.asc())
            .fetch()
            .map { mapToQuestionOption(it) }
    }

    override fun delete(id: UUID): Boolean {
        val deleted = dsl.deleteFrom(QUESTION_OPTIONS)
            .where(QUESTION_OPTIONS.ID.eq(id))
            .execute()

        return deleted > 0
    }

    override fun deleteByQuestionId(questionId: UUID): Int {
        return dsl.deleteFrom(QUESTION_OPTIONS)
            .where(QUESTION_OPTIONS.QUESTION_ID.eq(questionId))
            .execute()
    }

    private fun mapToQuestionOption(record: org.jooq.Record): QuestionOption {
        return QuestionOption(
            id = record.get(QUESTION_OPTIONS.ID)!!,
            questionId = record.get(QUESTION_OPTIONS.QUESTION_ID)!!,
            optionText = record.get(QUESTION_OPTIONS.OPTION_TEXT)!!,
            orderIndex = record.get(QUESTION_OPTIONS.ORDER_INDEX)!!,
            createdAt = record.get(QUESTION_OPTIONS.CREATED_AT)!!.toInstant()
        )
    }

    private fun Instant.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private fun LocalDateTime.toInstant(): Instant =
        this.toInstant(ZoneOffset.UTC)
}

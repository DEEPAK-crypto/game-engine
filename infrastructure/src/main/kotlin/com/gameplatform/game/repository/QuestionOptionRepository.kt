package com.gameplatform.game.repository

import com.gameplatform.game.domain.model.QuestionOption
import java.util.UUID

interface QuestionOptionRepository {
    fun save(option: QuestionOption): QuestionOption
    fun saveAll(options: List<QuestionOption>): List<QuestionOption>
    fun findById(id: UUID): QuestionOption?
    fun findByQuestionId(questionId: UUID): List<QuestionOption>
    fun findByQuestionIdOrderByIndex(questionId: UUID): List<QuestionOption>
    fun delete(id: UUID): Boolean
    fun deleteByQuestionId(questionId: UUID): Int
}

package com.gameplatform.game.repository

import com.gameplatform.game.domain.model.Question
import java.util.UUID

interface QuestionRepository {
    fun save(question: Question): Question
    fun saveAll(questions: List<Question>): List<Question>
    fun findById(id: UUID): Question?
    fun findByGameId(gameId: UUID): List<Question>
    fun findByGameIdOrderByIndex(gameId: UUID): List<Question>
    fun updateCorrectOption(id: UUID, correctOptionId: UUID): Boolean
    fun delete(id: UUID): Boolean
    fun deleteByGameId(gameId: UUID): Int
}
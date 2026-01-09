package com.gameplatform.game.service

import com.gameplatform.game.dto.ActiveQuestionResponse
import com.gameplatform.game.dto.CreateQuestionRequest
import com.gameplatform.game.dto.QuestionResponse
import java.util.UUID

interface QuestionService {
    fun addQuestions(gameId: UUID, requests: List<CreateQuestionRequest>): List<QuestionResponse>
    fun getQuestion(questionId: UUID): QuestionResponse
    fun getQuestionsByGame(gameId: UUID): List<QuestionResponse>
    fun getActiveQuestion(gameId: UUID): ActiveQuestionResponse?
    fun deleteQuestion(questionId: UUID)
}
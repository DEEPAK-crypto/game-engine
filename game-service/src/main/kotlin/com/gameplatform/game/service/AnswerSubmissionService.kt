package com.gameplatform.game.service

import com.gameplatform.game.dto.AnswerSubmissionResponse
import com.gameplatform.game.dto.SubmitAnswerRequest
import java.util.UUID

interface AnswerSubmissionService {
    /**
     * Submit an answer for the currently active question.
     * Validates the answer, checks for duplicates, and processes FIFO ordering.
     */
    fun submitAnswer(gameId: UUID, request: SubmitAnswerRequest): AnswerSubmissionResponse
}
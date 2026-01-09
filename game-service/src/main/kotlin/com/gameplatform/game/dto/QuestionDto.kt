package com.gameplatform.game.dto

import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionOption
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateQuestionRequest(
    val questionText: String,
    val options: List<CreateQuestionOptionRequest>,
    val correctOptionIndex: Int,
    val reward: BigDecimal,
    val durationSeconds: Int
)

data class CreateQuestionOptionRequest(
    val optionText: String
)

data class QuestionResponse(
    val id: UUID,
    val gameId: UUID,
    val questionText: String,
    val orderIndex: Int,
    val options: List<QuestionOptionResponse>,
    val correctOptionId: UUID?,
    val reward: BigDecimal,
    val durationSeconds: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(question: Question, options: List<QuestionOption>): QuestionResponse {
            return QuestionResponse(
                id = question.id,
                gameId = question.gameId,
                questionText = question.questionText,
                orderIndex = question.orderIndex,
                options = options.map { QuestionOptionResponse.from(it) },
                correctOptionId = question.correctOptionId,
                reward = question.reward,
                durationSeconds = question.durationSeconds,
                createdAt = question.createdAt
            )
        }
    }
}

data class QuestionOptionResponse(
    val id: UUID,
    val optionText: String,
    val orderIndex: Int
) {
    companion object {
        fun from(option: QuestionOption): QuestionOptionResponse {
            return QuestionOptionResponse(
                id = option.id,
                optionText = option.optionText,
                orderIndex = option.orderIndex
            )
        }
    }
}

data class ActiveQuestionResponse(
    val question: QuestionResponse,
    val startTime: Instant,
    val endTime: Instant,
    val remainingSeconds: Long
)

data class SubmitAnswerRequest(
    val userId: UUID,
    val selectedOptionId: UUID,
    val clientTimestamp: Instant = Instant.now()
)

data class AnswerSubmissionResponse(
    val turnId: UUID,
    val userId: UUID,
    val questionId: UUID,
    val selectedOptionId: UUID,
    val isCorrect: Boolean,
    val rewardAmount: BigDecimal,
    val rank: Int?,
    val submittedAt: Instant
)
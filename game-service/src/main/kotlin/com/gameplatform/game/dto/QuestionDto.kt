package com.gameplatform.game.dto

import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionOption
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateQuestionRequest(
    @field:NotBlank(message = "Question text is required")
    @field:Size(min = 10, max = 500, message = "Question text must be between 10 and 500 characters")
    val questionText: String,

    @field:NotEmpty(message = "Options are required")
    @field:Size(min = 2, max = 6, message = "Must have between 2 and 6 options")
    @field:Valid
    val options: List<CreateQuestionOptionRequest>,

    @field:Min(value = 0, message = "Correct option index must be non-negative")
    val correctOptionIndex: Int,

    @field:NotNull(message = "Reward is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Reward must be non-negative")
    val reward: BigDecimal,

    @field:Min(value = 5, message = "Duration must be at least 5 seconds")
    @field:Max(value = 300, message = "Duration must be at most 300 seconds")
    val durationSeconds: Int
)

data class CreateQuestionOptionRequest(
    @field:NotBlank(message = "Option text is required")
    @field:Size(min = 1, max = 200, message = "Option text must be between 1 and 200 characters")
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
    @field:NotNull(message = "User ID is required")
    val userId: UUID,

    @field:NotNull(message = "Selected option ID is required")
    val selectedOptionId: UUID,

    @field:NotNull(message = "Client timestamp is required")
    @field:PastOrPresent(message = "Client timestamp cannot be in the future")
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
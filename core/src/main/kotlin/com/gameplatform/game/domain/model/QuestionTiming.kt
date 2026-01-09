package com.gameplatform.game.domain.model

import java.util.UUID

data class QuestionTiming(
    val questionId: UUID,
    val orderIndex: Int,
    val durationSeconds: Int
) {
    companion object {
        fun from(question: Question): QuestionTiming {
            return QuestionTiming(
                questionId = question.id,
                orderIndex = question.orderIndex,
                durationSeconds = question.durationSeconds
            )
        }
    }
}

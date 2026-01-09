package com.gameplatform.game.domain.model

import java.time.Instant
import java.util.UUID

data class QuestionOption(
    val id: UUID,
    val questionId: UUID,
    val optionText: String,
    val orderIndex: Int,
    val createdAt: Instant
)

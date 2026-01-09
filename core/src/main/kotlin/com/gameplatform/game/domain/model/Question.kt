package com.gameplatform.game.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Question(
    val id: UUID,
    val gameId: UUID,
    val questionText: String,
    val orderIndex: Int,
    val correctOptionId: UUID?,
    val reward: BigDecimal,
    val durationSeconds: Int,
    val createdAt: Instant
) {
    fun hasCorrectOption(): Boolean = correctOptionId != null
}

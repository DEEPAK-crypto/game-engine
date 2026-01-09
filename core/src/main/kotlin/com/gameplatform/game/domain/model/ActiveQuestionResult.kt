package com.gameplatform.game.domain.model

import java.time.Instant

data class ActiveQuestionResult(
    val activeQuestion: QuestionTiming?,
    val expiresAt: Instant?,
    val isGameEnded: Boolean,
    val questionStartedAt: Instant? = null
) {
    fun hasActiveQuestion(): Boolean = activeQuestion != null && !isGameEnded

    fun getRemainingSeconds(currentTime: Instant): Long? {
        return expiresAt?.let {
            val remaining = java.time.Duration.between(currentTime, it).seconds
            if (remaining > 0) remaining else 0
        }
    }
}

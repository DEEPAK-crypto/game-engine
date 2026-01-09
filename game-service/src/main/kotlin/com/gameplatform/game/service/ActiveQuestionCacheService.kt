package com.gameplatform.game.service

import com.gameplatform.game.domain.model.ActiveQuestionResult
import java.time.Instant
import java.util.UUID

/**
 * Service for caching active question information in Redis to avoid
 * recalculating on every answer submission.
 */
interface ActiveQuestionCacheService {

    /**
     * Get the active question for a game from cache, or calculate and cache if not present.
     *
     * @param gameId The game ID
     * @param currentTime The current server time
     * @return The active question result, or null if no active question
     */
    fun getActiveQuestion(gameId: UUID, currentTime: Instant): ActiveQuestionResult?

    /**
     * Invalidate the cache for a game (e.g., when game starts or state changes).
     *
     * @param gameId The game ID
     */
    fun invalidate(gameId: UUID)
}

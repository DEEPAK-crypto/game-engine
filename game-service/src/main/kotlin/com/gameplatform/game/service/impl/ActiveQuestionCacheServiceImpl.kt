package com.gameplatform.game.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.gameplatform.game.domain.calculator.ActiveQuestionCalculator
import com.gameplatform.game.domain.model.ActiveQuestionResult
import com.gameplatform.game.domain.model.QuestionTiming
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.ActiveQuestionCacheService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ActiveQuestionCacheServiceImpl(
    private val redisTemplate: RedisTemplate<String, String>,
    private val gameRepository: GameRepository,
    private val questionRepository: QuestionRepository,
    private val objectMapper: ObjectMapper
) : ActiveQuestionCacheService {

    private val log = LoggerFactory.getLogger(ActiveQuestionCacheServiceImpl::class.java)

    companion object {
        private const val CACHE_KEY_PREFIX = "active_question:"
        private const val MIN_CACHE_TTL_SECONDS = 1L
    }

    override fun getActiveQuestion(gameId: UUID, currentTime: Instant): ActiveQuestionResult? {
        val cacheKey = getCacheKey(gameId)

        // Try to get from cache
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            try {
                val result = objectMapper.readValue(cached, CachedActiveQuestion::class.java)

                // Verify cache is still valid
                if (currentTime.isBefore(result.expiresAt)) {
                    log.debug("Active question cache hit", kv("gameId", gameId))
                    return result.toActiveQuestionResult()
                }

                // Cache expired, remove it
                redisTemplate.delete(cacheKey)
                log.debug("Active question cache expired", kv("gameId", gameId))
            } catch (e: Exception) {
                log.warn("Error deserializing cached active question", kv("gameId", gameId), kv("error", e.message))
                redisTemplate.delete(cacheKey)
            }
        }

        // Cache miss - calculate and cache
        log.debug("Active question cache miss", kv("gameId", gameId))
        return calculateAndCache(gameId, currentTime)
    }

    override fun invalidate(gameId: UUID) {
        val cacheKey = getCacheKey(gameId)
        redisTemplate.delete(cacheKey)
        log.debug("Active question cache invalidated", kv("gameId", gameId))
    }

    private fun calculateAndCache(gameId: UUID, currentTime: Instant): ActiveQuestionResult? {
        // Get game and questions
        val game = gameRepository.findById(gameId) ?: return null
        val startedAt = game.startedAt ?: return null

        val questions = questionRepository.findByGameIdOrderByIndex(gameId)
        if (questions.isEmpty()) {
            return null
        }

        // Calculate active question
        val questionTimings = questions.map { QuestionTiming.from(it) }
        val activeResult = ActiveQuestionCalculator.calculate(startedAt, questionTimings, currentTime)

        // Cache if there's an active question
        if (activeResult.hasActiveQuestion()) {
            val activeQuestionTiming = activeResult.activeQuestion!!
            val cached = CachedActiveQuestion(
                questionId = activeQuestionTiming.questionId,
                questionIndex = activeQuestionTiming.orderIndex,
                questionDuration = activeQuestionTiming.durationSeconds,
                expiresAt = activeResult.expiresAt!!,
                questionStartedAt = activeResult.questionStartedAt
            )

            val cacheKey = getCacheKey(gameId)
            val ttl = Duration.between(currentTime, activeResult.expiresAt).seconds

            // Only cache if TTL is positive
            if (ttl > 0) {
                val json = objectMapper.writeValueAsString(cached)
                redisTemplate.opsForValue().set(
                    cacheKey,
                    json,
                    Duration.ofSeconds(maxOf(ttl, MIN_CACHE_TTL_SECONDS))
                )

                log.debug(
                    "Active question cached",
                    kv("gameId", gameId),
                    kv("questionId", cached.questionId),
                    kv("ttl", ttl)
                )
            }
        }

        return activeResult
    }

    private fun getCacheKey(gameId: UUID): String = "$CACHE_KEY_PREFIX$gameId"

    /**
     * Cached representation of active question (lighter than full ActiveQuestionResult)
     */
    private data class CachedActiveQuestion(
        val questionId: UUID,
        val questionIndex: Int,
        val questionDuration: Int,
        val expiresAt: Instant,
        val questionStartedAt: Instant?
    ) {
        fun toActiveQuestionResult(): ActiveQuestionResult {
            return ActiveQuestionResult(
                activeQuestion = QuestionTiming(
                    questionId = questionId,
                    orderIndex = questionIndex,
                    durationSeconds = questionDuration
                ),
                expiresAt = expiresAt,
                isGameEnded = false, // If cached, game is active
                questionStartedAt = questionStartedAt
            )
        }
    }
}
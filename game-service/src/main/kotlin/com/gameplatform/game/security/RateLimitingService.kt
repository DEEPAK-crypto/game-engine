package com.gameplatform.game.security

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis-based rate limiting service using sliding window algorithm.
 */
@Service
class RateLimitingService(
    private val redisTemplate: StringRedisTemplate,
    private val securityProperties: SecurityProperties
) {
    private val logger = LoggerFactory.getLogger(RateLimitingService::class.java)

    /**
     * Check if a request should be allowed based on rate limits.
     *
     * @param identifier Unique identifier for the client (IP, user ID, etc.)
     * @param limit Optional custom limit (uses default if not specified)
     * @return RateLimitResult indicating whether the request is allowed
     */
    fun checkRateLimit(
        identifier: String,
        limit: Int? = null,
        windowSeconds: Long? = null
    ): RateLimitResult {
        if (!securityProperties.rateLimit.enabled) {
            return RateLimitResult(allowed = true, remaining = Int.MAX_VALUE, resetSeconds = 0)
        }

        val effectiveLimit = limit ?: securityProperties.rateLimit.defaultLimit
        val effectiveWindow = windowSeconds ?: securityProperties.rateLimit.windowSeconds
        val key = "$RATE_LIMIT_PREFIX$identifier"

        return try {
            val result = redisTemplate.execute(
                rateLimitScript,
                listOf(key),
                effectiveLimit.toString(),
                effectiveWindow.toString(),
                System.currentTimeMillis().toString()
            )

            val parts = result?.split(":") ?: listOf("1", effectiveLimit.toString(), effectiveWindow.toString())
            val allowed = parts[0] == "1"
            val remaining = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val resetSeconds = parts.getOrNull(2)?.toLongOrNull() ?: effectiveWindow

            RateLimitResult(
                allowed = allowed,
                remaining = remaining,
                resetSeconds = resetSeconds,
                limit = effectiveLimit
            )
        } catch (e: Exception) {
            logger.error("Rate limiting failed, allowing request: ${e.message}")
            // Fail open - allow the request if Redis is unavailable
            RateLimitResult(allowed = true, remaining = effectiveLimit, resetSeconds = 0)
        }
    }

    /**
     * Get rate limit for a specific user based on their roles.
     */
    fun getLimitForUser(userDetails: GameUserDetails?): Int {
        val baseLimit = securityProperties.rateLimit.defaultLimit

        return when {
            userDetails == null -> baseLimit
            userDetails.isAdmin() -> (baseLimit * securityProperties.rateLimit.adminMultiplier).toInt()
            else -> (baseLimit * securityProperties.rateLimit.authenticatedMultiplier).toInt()
        }
    }

    /**
     * Reset rate limit for an identifier.
     */
    fun resetRateLimit(identifier: String) {
        val key = "$RATE_LIMIT_PREFIX$identifier"
        redisTemplate.delete(key)
    }

    companion object {
        private const val RATE_LIMIT_PREFIX = "rate_limit:"

        /**
         * Lua script for atomic rate limiting using sliding window.
         * Returns: allowed:remaining:resetSeconds
         */
        private val rateLimitScript = RedisScript.of<String>(
            """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local window_start = now - (window * 1000)

            -- Remove old entries outside the window
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

            -- Count current requests in window
            local current = redis.call('ZCARD', key)

            if current < limit then
                -- Add new request
                redis.call('ZADD', key, now, now .. ':' .. math.random())
                redis.call('EXPIRE', key, window)
                local remaining = limit - current - 1
                return '1:' .. remaining .. ':' .. window
            else
                -- Rate limit exceeded
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local reset = window
                if oldest[2] then
                    reset = math.ceil((tonumber(oldest[2]) + (window * 1000) - now) / 1000)
                end
                return '0:0:' .. reset
            end
            """.trimIndent()
        )
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Int,
    val resetSeconds: Long,
    val limit: Int = 0
)

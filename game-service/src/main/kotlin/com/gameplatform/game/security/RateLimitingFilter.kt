package com.gameplatform.game.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rate limiting filter that enforces request limits per client.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RateLimitingFilter(
    private val rateLimitingService: RateLimitingService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(RateLimitingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Skip rate limiting for health checks and metrics
        if (shouldSkipRateLimiting(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val identifier = getClientIdentifier(request)
        val userDetails = getUserDetails()
        val limit = rateLimitingService.getLimitForUser(userDetails)

        val result = rateLimitingService.checkRateLimit(identifier, limit)

        // Add rate limit headers
        response.setHeader(HEADER_LIMIT, result.limit.toString())
        response.setHeader(HEADER_REMAINING, result.remaining.toString())
        response.setHeader(HEADER_RESET, result.resetSeconds.toString())

        if (!result.allowed) {
            logger.warn("Rate limit exceeded for identifier: $identifier")
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""
                {
                    "error": "Too many requests",
                    "message": "Rate limit exceeded. Please try again in ${result.resetSeconds} seconds.",
                    "retryAfter": ${result.resetSeconds}
                }
            """.trimIndent())
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun shouldSkipRateLimiting(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return SKIP_PATHS.any { path.startsWith(it) }
    }

    private fun getClientIdentifier(request: HttpServletRequest): String {
        // Prefer user ID if authenticated
        getUserDetails()?.let { userDetails ->
            return "user:${userDetails.userId}"
        }

        // Fall back to IP address
        val forwardedFor = request.getHeader("X-Forwarded-For")
        val ip = forwardedFor?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr
        return "ip:$ip"
    }

    private fun getUserDetails(): GameUserDetails? {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.principal as? GameUserDetails
    }

    companion object {
        const val HEADER_LIMIT = "X-RateLimit-Limit"
        const val HEADER_REMAINING = "X-RateLimit-Remaining"
        const val HEADER_RESET = "X-RateLimit-Reset"

        private val SKIP_PATHS = listOf(
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/info"
        )
    }
}

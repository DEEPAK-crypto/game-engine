package com.gameplatform.game.filter

import com.gameplatform.game.config.GracefulShutdownHandler
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that tracks in-flight requests and rejects new requests during graceful shutdown.
 * This filter runs early in the chain to ensure proper request tracking.
 */
@Component
@Order(0) // Run before TraceIdFilter
class GracefulShutdownFilter(
    private val shutdownHandler: GracefulShutdownHandler
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(GracefulShutdownFilter::class.java)

    companion object {
        // Paths that should always be allowed (health checks, metrics)
        private val ALLOWED_PATHS = setOf(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/prometheus"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        // Always allow health check endpoints during shutdown
        if (ALLOWED_PATHS.any { path.startsWith(it) }) {
            filterChain.doFilter(request, response)
            return
        }

        // Reject new requests if shutting down
        if (shutdownHandler.isShuttingDown()) {
            logger.warn("Rejecting request to {} during graceful shutdown", path)
            response.status = HttpStatus.SERVICE_UNAVAILABLE.value()
            response.setHeader("Connection", "close")
            response.setHeader("Retry-After", "30")
            response.contentType = "application/json"
            response.writer.write("""{"error": "Service is shutting down", "status": 503}""")
            return
        }

        // Track the request
        shutdownHandler.incrementActiveRequests()
        try {
            filterChain.doFilter(request, response)
        } finally {
            shutdownHandler.decrementActiveRequests()
        }
    }
}
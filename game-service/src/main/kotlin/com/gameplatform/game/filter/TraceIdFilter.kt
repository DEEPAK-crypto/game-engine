package com.gameplatform.game.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * Filter that adds a trace ID to every request for distributed tracing.
 * The trace ID is:
 * - Extracted from X-Trace-Id header if present
 * - Generated as a new UUID if not present
 * - Added to MDC (Mapped Diagnostic Context) for logging
 * - Added to response headers for client correlation
 */
@Component
@Order(1)
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val MDC_TRACE_ID_KEY = "traceId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Extract or generate trace ID
            val traceId = request.getHeader(TRACE_ID_HEADER)
                ?: UUID.randomUUID().toString()

            // Add to MDC for logging
            MDC.put(MDC_TRACE_ID_KEY, traceId)

            // Add to response headers
            response.setHeader(TRACE_ID_HEADER, traceId)

            // Continue the filter chain
            filterChain.doFilter(request, response)
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.remove(MDC_TRACE_ID_KEY)
        }
    }
}
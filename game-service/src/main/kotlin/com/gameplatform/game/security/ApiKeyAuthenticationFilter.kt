package com.gameplatform.game.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * API key authentication filter for service-to-service communication.
 * Checks X-API-Key header for valid API keys.
 */
@Component
class ApiKeyAuthenticationFilter(
    private val securityProperties: SecurityProperties
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Skip if already authenticated (e.g., by JWT)
        if (SecurityContextHolder.getContext().authentication?.isAuthenticated == true) {
            filterChain.doFilter(request, response)
            return
        }

        val apiKey = request.getHeader(API_KEY_HEADER)

        if (apiKey != null) {
            val apiKeyConfig = securityProperties.apiKeys.find { it.key == apiKey }

            if (apiKeyConfig != null) {
                val userDetails = GameUserDetails.serviceUser(
                    serviceName = apiKeyConfig.name,
                    roles = apiKeyConfig.roles
                )

                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
                logger.debug("Authenticated service: ${apiKeyConfig.name} with API key")
            } else {
                logger.warn("Invalid API key provided from: ${request.remoteAddr}")
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }
}

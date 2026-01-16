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
import java.util.*

/**
 * JWT authentication filter that validates tokens from Authorization header.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(BEARER_PREFIX.length)

        when (val result = jwtService.validateToken(token)) {
            is TokenValidationResult.Valid -> {
                val claims = result.claims
                val userId = claims.subject
                val username = claims["username"] as? String ?: "unknown"
                val roles = jwtService.extractRoles(claims)

                val userDetails = GameUserDetails(
                    userId = UUID.fromString(userId),
                    userName = username,
                    roles = roles
                )

                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
                logger.debug("Authenticated user: $username with roles: $roles")
            }
            is TokenValidationResult.Expired -> {
                logger.debug("Token expired for request: ${request.requestURI}")
            }
            is TokenValidationResult.Invalid -> {
                logger.debug("Invalid token for request: ${request.requestURI} - ${result.reason}")
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}

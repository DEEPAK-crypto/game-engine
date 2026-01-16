package com.gameplatform.game.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/**
 * Service for JWT token generation and validation.
 */
@Service
class JwtService(
    private val securityProperties: SecurityProperties
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(securityProperties.jwt.secret.toByteArray())
    }

    /**
     * Generate an access token for a user.
     */
    fun generateToken(
        userId: String,
        username: String,
        roles: Set<UserRole>,
        additionalClaims: Map<String, Any> = emptyMap()
    ): String {
        val now = Date()
        val expiration = Date(now.time + securityProperties.jwt.expirationSeconds * 1000)

        return Jwts.builder()
            .subject(userId)
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiration)
            .claim("username", username)
            .claim("roles", roles.map { it.name })
            .claims(additionalClaims)
            .signWith(secretKey)
            .compact()
    }

    /**
     * Generate a refresh token for a user.
     */
    fun generateRefreshToken(userId: String): String {
        val now = Date()
        val expiration = Date(now.time + securityProperties.jwt.refreshExpirationSeconds * 1000)

        return Jwts.builder()
            .subject(userId)
            .issuer(securityProperties.jwt.issuer)
            .issuedAt(now)
            .expiration(expiration)
            .claim("type", "refresh")
            .signWith(secretKey)
            .compact()
    }

    /**
     * Validate a token and extract claims.
     */
    fun validateToken(token: String): TokenValidationResult {
        return try {
            val claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload

            TokenValidationResult.Valid(claims)
        } catch (e: ExpiredJwtException) {
            logger.debug("Token expired: ${e.message}")
            TokenValidationResult.Expired
        } catch (e: Exception) {
            logger.debug("Token validation failed: ${e.message}")
            TokenValidationResult.Invalid(e.message ?: "Unknown error")
        }
    }

    /**
     * Extract user ID from token without full validation.
     */
    fun extractUserId(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
            claims.subject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract roles from claims.
     */
    @Suppress("UNCHECKED_CAST")
    fun extractRoles(claims: Claims): Set<UserRole> {
        val rolesList = claims["roles"] as? List<String> ?: return emptySet()
        return rolesList.mapNotNull { roleName ->
            try {
                UserRole.valueOf(roleName)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toSet()
    }
}

sealed class TokenValidationResult {
    data class Valid(val claims: Claims) : TokenValidationResult()
    data object Expired : TokenValidationResult()
    data class Invalid(val reason: String) : TokenValidationResult()
}

package com.gameplatform.game.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for security settings.
 */
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val jwt: JwtProperties = JwtProperties(),
    val rateLimit: RateLimitProperties = RateLimitProperties(),
    val apiKeys: List<ApiKeyConfig> = emptyList()
)

data class JwtProperties(
    /** Secret key for signing JWTs (should be at least 256 bits) */
    val secret: String = "CHANGE-ME-IN-PRODUCTION-256-BIT-SECRET-KEY-HERE",
    /** Token expiration in seconds (default: 1 hour) */
    val expirationSeconds: Long = 3600,
    /** Refresh token expiration in seconds (default: 7 days) */
    val refreshExpirationSeconds: Long = 604800,
    /** Issuer claim for JWT */
    val issuer: String = "game-platform"
)

data class RateLimitProperties(
    /** Whether rate limiting is enabled */
    val enabled: Boolean = true,
    /** Default requests per window */
    val defaultLimit: Int = 100,
    /** Window size in seconds */
    val windowSeconds: Long = 60,
    /** Authenticated user limit multiplier */
    val authenticatedMultiplier: Double = 2.0,
    /** Admin limit multiplier */
    val adminMultiplier: Double = 10.0
)

data class ApiKeyConfig(
    /** Service name */
    val name: String,
    /** API key value */
    val key: String,
    /** Allowed roles for this API key */
    val roles: List<String> = listOf("SERVICE")
)

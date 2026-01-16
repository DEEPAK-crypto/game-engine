package com.gameplatform.game.security

import io.jsonwebtoken.Claims
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Authentication controller for login, registration, and token refresh.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtService: JwtService,
    private val securityProperties: SecurityProperties
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    /**
     * Login with username/password (demo implementation).
     * In production, this would validate against a user database.
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        // Demo implementation - in production, validate against user DB
        logger.info("Login attempt for user: ${request.username}")

        // For demo purposes, accept any login and create a user
        val userId = UUID.randomUUID()
        val roles = when {
            request.username.startsWith("admin") -> setOf(UserRole.ADMIN)
            request.username.startsWith("host") -> setOf(UserRole.HOST)
            else -> setOf(UserRole.PLAYER)
        }

        val accessToken = jwtService.generateToken(
            userId = userId.toString(),
            username = request.username,
            roles = roles
        )

        val refreshToken = jwtService.generateRefreshToken(userId.toString())

        return ResponseEntity.ok(
            AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = securityProperties.jwt.expirationSeconds,
                tokenType = "Bearer",
                userId = userId,
                username = request.username,
                roles = roles.map { it.name }
            )
        )
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Any> {
        return when (val result = jwtService.validateToken(request.refreshToken)) {
            is TokenValidationResult.Valid -> {
                val claims = result.claims
                val tokenType = claims["type"] as? String

                if (tokenType != "refresh") {
                    return ResponseEntity.badRequest().body(
                        ErrorResponse("Invalid token type", "Expected refresh token")
                    )
                }

                val userId = claims.subject
                // In production, fetch user from DB to get current roles
                val roles = setOf(UserRole.PLAYER)

                val accessToken = jwtService.generateToken(
                    userId = userId,
                    username = "user", // In production, get from DB
                    roles = roles
                )

                ResponseEntity.ok(
                    TokenRefreshResponse(
                        accessToken = accessToken,
                        expiresIn = securityProperties.jwt.expirationSeconds,
                        tokenType = "Bearer"
                    )
                )
            }
            is TokenValidationResult.Expired -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorResponse("Token expired", "Refresh token has expired, please login again")
                )
            }
            is TokenValidationResult.Invalid -> {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorResponse("Invalid token", result.reason)
                )
            }
        }
    }

    /**
     * Register a new user (demo implementation).
     */
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        logger.info("Registration request for user: ${request.username}")

        // Demo implementation - in production, create user in DB
        val userId = UUID.randomUUID()
        val roles = setOf(UserRole.PLAYER)

        val accessToken = jwtService.generateToken(
            userId = userId.toString(),
            username = request.username,
            roles = roles
        )

        val refreshToken = jwtService.generateRefreshToken(userId.toString())

        return ResponseEntity.status(HttpStatus.CREATED).body(
            AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = securityProperties.jwt.expirationSeconds,
                tokenType = "Bearer",
                userId = userId,
                username = request.username,
                roles = roles.map { it.name }
            )
        )
    }

    /**
     * Validate current token.
     */
    @GetMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<Any> {
        if (!authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(
                ErrorResponse("Invalid format", "Expected Bearer token")
            )
        }

        val token = authHeader.substring(7)
        return when (val result = jwtService.validateToken(token)) {
            is TokenValidationResult.Valid -> {
                val claims = result.claims
                ResponseEntity.ok(
                    TokenValidationResponse(
                        valid = true,
                        userId = claims.subject,
                        username = claims["username"] as? String,
                        roles = jwtService.extractRoles(claims).map { it.name },
                        expiresAt = claims.expiration?.time
                    )
                )
            }
            is TokenValidationResult.Expired -> {
                ResponseEntity.ok(TokenValidationResponse(valid = false, reason = "Token expired"))
            }
            is TokenValidationResult.Invalid -> {
                ResponseEntity.ok(TokenValidationResponse(valid = false, reason = result.reason))
            }
        }
    }
}

// Request/Response DTOs

data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class RegisterRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "Display name is required")
    val displayName: String
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String,
    val userId: UUID,
    val username: String,
    val roles: List<String>
)

data class TokenRefreshResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String
)

data class TokenValidationResponse(
    val valid: Boolean,
    val userId: String? = null,
    val username: String? = null,
    val roles: List<String>? = null,
    val expiresAt: Long? = null,
    val reason: String? = null
)

data class ErrorResponse(
    val error: String,
    val message: String
)

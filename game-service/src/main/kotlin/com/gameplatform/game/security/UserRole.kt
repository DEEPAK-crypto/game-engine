package com.gameplatform.game.security

/**
 * User roles for authorization.
 */
enum class UserRole {
    PLAYER,  // Regular player
    HOST,    // Can create and manage games
    ADMIN;   // Full administrative access

    fun toAuthority(): String = "ROLE_$name"

    companion object {
        fun fromAuthority(authority: String): UserRole {
            val roleName = authority.removePrefix("ROLE_")
            return entries.find { it.name == roleName }
                ?: throw IllegalArgumentException("Unknown role: $authority")
        }
    }
}
package com.gameplatform.game.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.security.Principal
import java.util.*

/**
 * Custom UserDetails implementation for game platform users.
 */
data class GameUserDetails(
    val userId: UUID,
    val userName: String,
    val roles: Set<UserRole>,
    val isApiKey: Boolean = false,
    val serviceName: String? = null
) : UserDetails, Principal {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority(it.toAuthority()) }
    }

    override fun getPassword(): String? = null

    override fun getUsername(): String = userName

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    override fun getName(): String = userId.toString()

    fun hasRole(role: UserRole): Boolean = roles.contains(role)

    fun isAdmin(): Boolean = hasRole(UserRole.ADMIN)

    fun isHost(): Boolean = hasRole(UserRole.HOST) || isAdmin()

    companion object {
        /**
         * Create a service user from API key authentication.
         */
        fun serviceUser(serviceName: String, roles: List<String>): GameUserDetails {
            val userRoles = roles.mapNotNull { roleName ->
                try {
                    UserRole.valueOf(roleName)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.toSet()

            return GameUserDetails(
                userId = UUID(0, 0), // Service user ID
                userName = serviceName,
                roles = userRoles,
                isApiKey = true,
                serviceName = serviceName
            )
        }
    }
}

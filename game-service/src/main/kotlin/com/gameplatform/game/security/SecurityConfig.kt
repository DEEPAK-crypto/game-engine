package com.gameplatform.game.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security configuration for the game platform.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
    private val securityProperties: SecurityProperties
) {

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Public endpoints
                auth.requestMatchers(HttpMethod.GET, "/api/games/*/public/**").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()

                // Player endpoints
                auth.requestMatchers(HttpMethod.POST, "/api/games/*/join").authenticated()
                auth.requestMatchers(HttpMethod.POST, "/api/games/*/answer").authenticated()
                auth.requestMatchers(HttpMethod.GET, "/api/games/*/leaderboard").authenticated()
                auth.requestMatchers(HttpMethod.GET, "/api/players/**").authenticated()

                // Host endpoints
                auth.requestMatchers(HttpMethod.POST, "/api/games").hasAnyRole("HOST", "ADMIN")
                auth.requestMatchers(HttpMethod.PUT, "/api/games/**").hasAnyRole("HOST", "ADMIN")
                auth.requestMatchers(HttpMethod.DELETE, "/api/games/**").hasAnyRole("HOST", "ADMIN")
                auth.requestMatchers(HttpMethod.POST, "/api/games/*/questions/**").hasAnyRole("HOST", "ADMIN")
                auth.requestMatchers("/api/schedules/**").hasAnyRole("HOST", "ADMIN")

                // Admin endpoints
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Service-to-service endpoints
                auth.requestMatchers("/api/internal/**").hasRole("SERVICE")

                // Default: require authentication
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyAuthenticationFilter, JwtAuthenticationFilter::class.java)
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, authException ->
                    response.status = 401
                    response.contentType = "application/json"
                    response.writer.write("""
                        {
                            "error": "Unauthorized",
                            "message": "${authException.message ?: "Authentication required"}"
                        }
                    """.trimIndent())
                }
                exceptions.accessDeniedHandler { _, response, accessDeniedException ->
                    response.status = 403
                    response.contentType = "application/json"
                    response.writer.write("""
                        {
                            "error": "Forbidden",
                            "message": "${accessDeniedException.message ?: "Access denied"}"
                        }
                    """.trimIndent())
                }
            }

        return http.build()
    }

    @Bean
    @Order(2)
    fun webSocketSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/ws/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Allow WebSocket connections - authentication handled in handshake
                auth.anyRequest().permitAll()
            }

        return http.build()
    }

    @Bean
    @Order(3)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/actuator/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                // Health and info are public
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Prometheus metrics for scraping
                auth.requestMatchers("/actuator/prometheus").permitAll()
                // Other actuator endpoints require admin
                auth.anyRequest().hasRole("ADMIN")
            }

        return http.build()
    }

    @Bean
    @Order(4)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { auth ->
                // OpenAPI docs
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Everything else requires authentication
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf(
                "Authorization",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset"
            )
            allowCredentials = true
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}

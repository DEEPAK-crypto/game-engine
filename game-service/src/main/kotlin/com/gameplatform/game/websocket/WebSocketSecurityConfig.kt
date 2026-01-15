package com.gameplatform.game.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer

/**
 * WebSocket security configuration.
 * Currently allows all connections - add authentication in M8.
 */
@Configuration
class WebSocketSecurityConfig : AbstractSecurityWebSocketMessageBrokerConfigurer() {

    override fun configureInbound(messages: MessageSecurityMetadataSourceRegistry) {
        messages
            // Allow all connections for now (auth will be added in M8)
            .simpTypeMatchers(
                SimpMessageType.CONNECT,
                SimpMessageType.DISCONNECT,
                SimpMessageType.HEARTBEAT
            ).permitAll()
            // Allow subscriptions to game topics
            .simpSubscribeDestMatchers("/topic/**", "/queue/**", "/user/**").permitAll()
            // Allow messages to app destinations
            .simpDestMatchers("/app/**").permitAll()
            // Deny everything else by default
            .anyMessage().permitAll()
    }

    override fun sameOriginDisabled(): Boolean {
        // Disable same-origin policy for development
        // Enable in production with proper CORS configuration
        return true
    }
}

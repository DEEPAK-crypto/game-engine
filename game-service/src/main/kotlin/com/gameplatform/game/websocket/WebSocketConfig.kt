package com.gameplatform.game.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Enable simple in-memory broker for subscriptions
        // Clients subscribe to /topic/game/{gameId} for game events
        registry.enableSimpleBroker("/topic", "/queue")

        // Prefix for messages from clients to server
        registry.setApplicationDestinationPrefixes("/app")

        // Prefix for user-specific messages
        registry.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // WebSocket endpoint for STOMP connections
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()

        // Raw WebSocket endpoint (without SockJS fallback)
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }
}

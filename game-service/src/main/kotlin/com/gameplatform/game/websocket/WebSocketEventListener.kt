package com.gameplatform.game.websocket

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent

/**
 * Listens to WebSocket session lifecycle events.
 */
@Component
class WebSocketEventListener(
    private val sessionRegistry: PlayerSessionRegistry
) {
    private val logger = LoggerFactory.getLogger(WebSocketEventListener::class.java)

    @EventListener
    fun handleWebSocketConnectListener(event: SessionConnectedEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId

        logger.info("WebSocket connected: sessionId=$sessionId")
    }

    @EventListener
    fun handleWebSocketDisconnectListener(event: SessionDisconnectEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId

        if (sessionId != null) {
            sessionRegistry.removeSession(sessionId)
            logger.info("WebSocket disconnected: sessionId=$sessionId")
        }
    }

    @EventListener
    fun handleSubscribeEvent(event: SessionSubscribeEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val destination = headerAccessor.destination
        val sessionId = headerAccessor.sessionId

        logger.debug("WebSocket subscription: sessionId=$sessionId, destination=$destination")
    }

    @EventListener
    fun handleUnsubscribeEvent(event: SessionUnsubscribeEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId

        logger.debug("WebSocket unsubscription: sessionId=$sessionId")
    }
}
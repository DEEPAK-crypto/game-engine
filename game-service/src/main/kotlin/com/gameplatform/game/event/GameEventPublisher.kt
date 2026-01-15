package com.gameplatform.game.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Publishes game events to WebSocket clients.
 * Uses Redis Pub/Sub for multi-pod deployment to ensure all pods
 * receive events and broadcast to their connected clients.
 */
@Service
class GameEventPublisher(
    private val messagingTemplate: SimpMessagingTemplate,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GameEventPublisher::class.java)

    companion object {
        const val REDIS_CHANNEL = "game-events"
    }

    /**
     * Publish an event to all clients subscribed to a game.
     * Event is first published to Redis, then Redis subscriber broadcasts to local WebSocket clients.
     */
    fun publishToGame(event: GameEvent) {
        logger.debug("Publishing event ${event.type} for game ${event.gameId}")

        try {
            // Serialize and publish to Redis for multi-pod distribution
            val message = RedisEventMessage(
                destination = "/topic/game/${event.gameId}",
                payload = objectMapper.writeValueAsString(event),
                eventType = event.type
            )
            redisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(message))
        } catch (e: Exception) {
            logger.error("Failed to publish event to Redis: ${e.message}", e)
            // Fallback to direct WebSocket publish (works for single-pod)
            broadcastLocally(event)
        }
    }

    /**
     * Publish an event to a specific user.
     */
    fun publishToUser(userId: UUID, event: GameEvent) {
        logger.debug("Publishing event ${event.type} to user $userId")

        try {
            val message = RedisEventMessage(
                destination = "/user/$userId/queue/events",
                payload = objectMapper.writeValueAsString(event),
                eventType = event.type,
                userId = userId.toString()
            )
            redisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(message))
        } catch (e: Exception) {
            logger.error("Failed to publish user event to Redis: ${e.message}", e)
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/events",
                event
            )
        }
    }

    /**
     * Broadcast event directly to local WebSocket clients.
     * Called by Redis subscriber or as fallback.
     */
    fun broadcastLocally(event: GameEvent) {
        messagingTemplate.convertAndSend("/topic/game/${event.gameId}", event)
    }

    /**
     * Broadcast to a specific destination.
     */
    fun broadcastToDestination(destination: String, payload: Any) {
        messagingTemplate.convertAndSend(destination, payload)
    }

    /**
     * Broadcast to a specific user.
     */
    fun broadcastToUser(userId: String, destination: String, payload: Any) {
        messagingTemplate.convertAndSendToUser(userId, destination, payload)
    }
}

/**
 * Message format for Redis Pub/Sub.
 */
data class RedisEventMessage(
    val destination: String,
    val payload: String,
    val eventType: String,
    val userId: String? = null
)
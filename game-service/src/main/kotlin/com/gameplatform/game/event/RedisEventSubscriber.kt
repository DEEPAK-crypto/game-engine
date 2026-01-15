package com.gameplatform.game.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Listens to Redis Pub/Sub channel for game events
 * and broadcasts them to local WebSocket clients.
 */
@Component
class RedisEventSubscriber(
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) : MessageListener {

    private val logger = LoggerFactory.getLogger(RedisEventSubscriber::class.java)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val messageBody = String(message.body)
            val eventMessage = objectMapper.readValue(messageBody, RedisEventMessage::class.java)

            logger.debug("Received Redis event: ${eventMessage.eventType} -> ${eventMessage.destination}")

            // Parse the payload back to an object
            val payload = objectMapper.readTree(eventMessage.payload)

            if (eventMessage.userId != null) {
                // User-specific message
                messagingTemplate.convertAndSendToUser(
                    eventMessage.userId,
                    eventMessage.destination.substringAfter("/user/${eventMessage.userId}"),
                    payload
                )
            } else {
                // Broadcast message
                messagingTemplate.convertAndSend(eventMessage.destination, payload)
            }
        } catch (e: Exception) {
            logger.error("Failed to process Redis event message: ${e.message}", e)
        }
    }
}
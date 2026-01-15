package com.gameplatform.game.event

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

/**
 * Configures Redis Pub/Sub for WebSocket event distribution
 * across multiple pod instances.
 */
@Configuration
class RedisEventConfig {

    @Bean
    fun gameEventsTopic(): ChannelTopic {
        return ChannelTopic(GameEventPublisher.REDIS_CHANNEL)
    }

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        redisEventSubscriber: RedisEventSubscriber,
        gameEventsTopic: ChannelTopic
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(
            MessageListenerAdapter(redisEventSubscriber, "onMessage"),
            gameEventsTopic
        )
        return container
    }
}
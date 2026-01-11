package com.gameplatform.game.config

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextClosedEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Configuration
class GracefulShutdownConfig {

    @Bean
    fun gracefulShutdownHandler(): GracefulShutdownHandler {
        return GracefulShutdownHandler()
    }
}

class GracefulShutdownHandler : ApplicationListener<ContextClosedEvent> {

    private val logger = LoggerFactory.getLogger(GracefulShutdownHandler::class.java)
    private val shuttingDown = AtomicBoolean(false)
    private val activeRequests = AtomicInteger(0)

    fun isShuttingDown(): Boolean = shuttingDown.get()

    fun incrementActiveRequests(): Int = activeRequests.incrementAndGet()

    fun decrementActiveRequests(): Int = activeRequests.decrementAndGet()

    fun getActiveRequestCount(): Int = activeRequests.get()

    override fun onApplicationEvent(event: ContextClosedEvent) {
        logger.info("Graceful shutdown initiated...")
        shuttingDown.set(true)

        val startTime = System.currentTimeMillis()
        val timeoutMs = 25_000L // 25 seconds (leaving 5s buffer from the 30s timeout)

        // Wait for in-flight requests to complete
        while (activeRequests.get() > 0 && (System.currentTimeMillis() - startTime) < timeoutMs) {
            val remaining = activeRequests.get()
            logger.info("Waiting for {} in-flight request(s) to complete...", remaining)
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("Shutdown wait interrupted")
                break
            }
        }

        val finalCount = activeRequests.get()
        if (finalCount > 0) {
            logger.warn("Shutdown proceeding with {} request(s) still in progress", finalCount)
        } else {
            logger.info("All in-flight requests completed. Shutdown complete.")
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Graceful shutdown completed in {}ms", elapsed)
    }
}
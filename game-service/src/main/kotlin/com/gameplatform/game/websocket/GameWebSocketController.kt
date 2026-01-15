package com.gameplatform.game.websocket

import com.gameplatform.game.event.PlayerCountEvent
import com.gameplatform.game.event.GameEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket controller for game-related messaging.
 */
@Controller
class GameWebSocketController(
    private val eventPublisher: GameEventPublisher,
    private val sessionRegistry: PlayerSessionRegistry
) {
    private val logger = LoggerFactory.getLogger(GameWebSocketController::class.java)

    /**
     * Handle player joining a game room.
     */
    @MessageMapping("/game/{gameId}/join")
    @SendTo("/topic/game/{gameId}")
    fun joinGame(
        @DestinationVariable gameId: UUID,
        headerAccessor: SimpMessageHeaderAccessor
    ): PlayerCountEvent {
        val sessionId = headerAccessor.sessionId ?: return PlayerCountEvent(gameId, 0)
        val userId = headerAccessor.user?.name

        logger.info("Player joining game $gameId, sessionId=$sessionId, userId=$userId")

        sessionRegistry.addPlayer(gameId, sessionId, userId)
        val playerCount = sessionRegistry.getPlayerCount(gameId)

        return PlayerCountEvent(gameId, playerCount)
    }

    /**
     * Handle player leaving a game room.
     */
    @MessageMapping("/game/{gameId}/leave")
    @SendTo("/topic/game/{gameId}")
    fun leaveGame(
        @DestinationVariable gameId: UUID,
        headerAccessor: SimpMessageHeaderAccessor
    ): PlayerCountEvent {
        val sessionId = headerAccessor.sessionId ?: return PlayerCountEvent(gameId, 0)

        logger.info("Player leaving game $gameId, sessionId=$sessionId")

        sessionRegistry.removePlayer(gameId, sessionId)
        val playerCount = sessionRegistry.getPlayerCount(gameId)

        return PlayerCountEvent(gameId, playerCount)
    }

    /**
     * Ping/heartbeat for connection keepalive.
     */
    @MessageMapping("/ping")
    fun ping(headerAccessor: SimpMessageHeaderAccessor): String {
        return "pong"
    }
}

/**
 * Tracks active player sessions per game.
 */
@Controller
class PlayerSessionRegistry {

    private val logger = LoggerFactory.getLogger(PlayerSessionRegistry::class.java)

    // gameId -> Set of sessionIds
    private val gameSessions = ConcurrentHashMap<UUID, MutableSet<String>>()

    // sessionId -> userId (optional)
    private val sessionUsers = ConcurrentHashMap<String, String?>()

    fun addPlayer(gameId: UUID, sessionId: String, userId: String?) {
        gameSessions.computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        sessionUsers[sessionId] = userId
        logger.debug("Player added to game $gameId: session=$sessionId, user=$userId")
    }

    fun removePlayer(gameId: UUID, sessionId: String) {
        gameSessions[gameId]?.remove(sessionId)
        sessionUsers.remove(sessionId)
        logger.debug("Player removed from game $gameId: session=$sessionId")

        // Clean up empty game sessions
        if (gameSessions[gameId]?.isEmpty() == true) {
            gameSessions.remove(gameId)
        }
    }

    fun removeSession(sessionId: String) {
        sessionUsers.remove(sessionId)
        gameSessions.forEach { (gameId, sessions) ->
            if (sessions.remove(sessionId)) {
                logger.debug("Session $sessionId removed from game $gameId")
            }
        }
    }

    fun getPlayerCount(gameId: UUID): Int {
        return gameSessions[gameId]?.size ?: 0
    }

    fun getConnectedPlayers(gameId: UUID): Set<String> {
        return gameSessions[gameId]?.toSet() ?: emptySet()
    }

    fun getUserId(sessionId: String): String? {
        return sessionUsers[sessionId]
    }
}
package com.gameplatform.game.service

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.dto.GameResponse
import java.time.Instant
import java.util.UUID

interface GameService {
    fun createGame(request: CreateGameRequest): GameResponse
    fun getGame(gameId: UUID): GameResponse
    fun getAllGames(): List<GameResponse>
    fun getGamesByStatus(status: GameStatus): List<GameResponse>
    fun startGame(gameId: UUID, startAt: Instant = Instant.now()): GameResponse
    fun completeGame(gameId: UUID, endAt: Instant = Instant.now()): GameResponse
    fun deleteGame(gameId: UUID)
}
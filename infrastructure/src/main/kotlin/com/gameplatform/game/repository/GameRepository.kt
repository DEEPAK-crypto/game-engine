package com.gameplatform.game.repository

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.model.Game
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface GameRepository {
    fun save(game: Game): Game
    fun findById(id: UUID): Game?
    fun findAll(): List<Game>
    fun findByStatus(status: GameStatus): List<Game>
    fun updateStatus(id: UUID, status: GameStatus, timestamp: Instant): Boolean
    fun updateRemainingBudget(id: UUID, newBudget: BigDecimal): Boolean
    fun delete(id: UUID): Boolean
}
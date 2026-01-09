package com.gameplatform.game.service

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.exception.GameAlreadyStartedException
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.testconfig.TestcontainersConfiguration
import com.gameplatform.game.testutil.TestDataFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class GameServiceIntegrationTest {

    @Autowired
    private lateinit var gameService: GameService

    @Test
    fun `should create game successfully`() {
        // Given
        val request = TestDataFactory.createGameRequest(
            name = "Service Test Game",
            initialBudget = BigDecimal("2000.00")
        )

        // When
        val response = gameService.createGame(request)

        // Then
        assertNotNull(response.id)
        assertEquals(request.name, response.name)
        assertEquals(request.initialBudget, response.initialBudget)
        assertEquals(GameStatus.DRAFT, response.status)
    }

    @Test
    fun `should start game successfully`() {
        // Given
        val request = TestDataFactory.createGameRequest()
        val game = gameService.createGame(request)

        // When
        val started = gameService.startGame(game.id)

        // Then
        assertEquals(GameStatus.ACTIVE, started.status)
        assertNotNull(started.startedAt)
    }

    @Test
    fun `should not start already started game`() {
        // Given
        val request = TestDataFactory.createGameRequest()
        val game = gameService.createGame(request)
        gameService.startGame(game.id)

        // When/Then
        assertThrows<GameAlreadyStartedException> {
            gameService.startGame(game.id)
        }
    }

    @Test
    fun `should complete game successfully`() {
        // Given
        val request = TestDataFactory.createGameRequest()
        val game = gameService.createGame(request)
        gameService.startGame(game.id)

        // When
        val completed = gameService.completeGame(game.id)

        // Then
        assertEquals(GameStatus.COMPLETED, completed.status)
        assertNotNull(completed.endedAt)
    }

    @Test
    fun `should get game by id`() {
        // Given
        val request = TestDataFactory.createGameRequest()
        val created = gameService.createGame(request)

        // When
        val retrieved = gameService.getGame(created.id)

        // Then
        assertEquals(created.id, retrieved.id)
        assertEquals(created.name, retrieved.name)
    }

    @Test
    fun `should throw exception when game not found`() {
        // Given
        val nonExistentId = java.util.UUID.randomUUID()

        // When/Then
        assertThrows<GameNotFoundException> {
            gameService.getGame(nonExistentId)
        }
    }

    @Test
    fun `should filter games by status`() {
        // Given
        val draftRequest = TestDataFactory.createGameRequest(name = "Draft Game")
        val activeRequest = TestDataFactory.createGameRequest(name = "Active Game")

        val draftGame = gameService.createGame(draftRequest)
        val activeGame = gameService.createGame(activeRequest)
        gameService.startGame(activeGame.id)

        // When
        val draftGames = gameService.getGamesByStatus(GameStatus.DRAFT)
        val activeGames = gameService.getGamesByStatus(GameStatus.ACTIVE)

        // Then
        assertTrue(draftGames.any { it.id == draftGame.id })
        assertTrue(activeGames.any { it.id == activeGame.id })
    }
}
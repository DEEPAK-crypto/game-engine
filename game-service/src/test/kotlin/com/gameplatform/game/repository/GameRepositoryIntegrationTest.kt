package com.gameplatform.game.repository

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.testconfig.TestcontainersConfiguration
import com.gameplatform.game.testutil.TestDataFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
@Transactional
class GameRepositoryIntegrationTest {

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Test
    fun `should save and retrieve game`() {
        // Given
        val game = TestDataFactory.createGame(
            name = "Integration Test Game",
            initialBudget = BigDecimal("500.00")
        )

        // When
        val saved = gameRepository.save(game)
        val retrieved = gameRepository.findById(game.id)

        // Then
        assertNotNull(retrieved)
        assertEquals(game.id, retrieved?.id)
        assertEquals(game.name, retrieved?.name)
        assertEquals(game.initialBudget, retrieved?.initialBudget)
        assertEquals(game.status, retrieved?.status)
    }

    @Test
    fun `should find games by status`() {
        // Given
        val draftGame = TestDataFactory.createGame(name = "Draft Game", status = GameStatus.DRAFT)
        val activeGame = TestDataFactory.createGame(name = "Active Game", status = GameStatus.ACTIVE)

        gameRepository.save(draftGame)
        gameRepository.save(activeGame)

        // When
        val draftGames = gameRepository.findByStatus(GameStatus.DRAFT)
        val activeGames = gameRepository.findByStatus(GameStatus.ACTIVE)

        // Then
        assertTrue(draftGames.any { it.id == draftGame.id })
        assertTrue(activeGames.any { it.id == activeGame.id })
    }

    @Test
    fun `should update game status`() {
        // Given
        val game = TestDataFactory.createGame(status = GameStatus.DRAFT)
        gameRepository.save(game)
        val now = Instant.now()

        // When
        val updated = gameRepository.updateStatus(game.id, GameStatus.ACTIVE, now)
        val retrieved = gameRepository.findById(game.id)

        // Then
        assertTrue(updated)
        assertEquals(GameStatus.ACTIVE, retrieved?.status)
        assertEquals(now, retrieved?.startedAt)
    }

    @Test
    fun `should update remaining budget`() {
        // Given
        val game = TestDataFactory.createGame(
            initialBudget = BigDecimal("1000.00"),
            remainingBudget = BigDecimal("1000.00")
        )
        gameRepository.save(game)
        val newBudget = BigDecimal("750.00")

        // When
        val updated = gameRepository.updateRemainingBudget(game.id, newBudget)
        val retrieved = gameRepository.findById(game.id)

        // Then
        assertTrue(updated)
        assertEquals(newBudget, retrieved?.remainingBudget)
    }

    @Test
    fun `should delete game`() {
        // Given
        val game = TestDataFactory.createGame()
        gameRepository.save(game)

        // When
        val deleted = gameRepository.delete(game.id)
        val retrieved = gameRepository.findById(game.id)

        // Then
        assertTrue(deleted)
        assertNull(retrieved)
    }
}
package com.gameplatform.game.service

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.exception.BudgetAllocationException
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InsufficientBudgetException
import com.gameplatform.game.metrics.GameMetrics
import com.gameplatform.game.repository.BudgetTransactionRepository
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.service.impl.BudgetServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class BudgetServiceTest {

    private lateinit var budgetService: BudgetService
    private lateinit var gameRepository: GameRepository
    private lateinit var budgetTransactionRepository: BudgetTransactionRepository
    private lateinit var gameMetrics: GameMetrics

    @BeforeEach
    fun setup() {
        gameRepository = mock()
        budgetTransactionRepository = mock()
        gameMetrics = mock()

        budgetService = BudgetServiceImpl(
            gameRepository,
            budgetTransactionRepository,
            gameMetrics
        )
    }

    @Test
    fun `should allocate question reward successfully when budget is sufficient`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("100.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(gameRepository.updateRemainingBudget(gameId, BigDecimal("900.00"))).thenReturn(true)
        whenever(budgetTransactionRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = budgetService.allocateQuestionReward(gameId, questionId, amount)

        // Then
        assertThat(result).isTrue()
        verify(gameRepository).updateRemainingBudget(gameId, BigDecimal("900.00"))
        verify(budgetTransactionRepository).save(any())
        verify(gameMetrics).recordBudgetAllocated(amount, gameId)
    }

    @Test
    fun `should return false when budget is insufficient for allocation`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("1500.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When
        val result = budgetService.allocateQuestionReward(gameId, questionId, amount)

        // Then
        assertThat(result).isFalse()
        verify(gameRepository, never()).updateRemainingBudget(any(), any())
        verify(budgetTransactionRepository, never()).save(any())
    }

    @Test
    fun `should throw GameNotFoundException when game does not exist for allocation`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("100.00")

        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            budgetService.allocateQuestionReward(gameId, questionId, amount)
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should throw BudgetAllocationException when update fails`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("100.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(gameRepository.updateRemainingBudget(gameId, BigDecimal("900.00"))).thenReturn(false)

        // When & Then
        assertThatThrownBy {
            budgetService.allocateQuestionReward(gameId, questionId, amount)
        }.isInstanceOf(BudgetAllocationException::class.java)
    }

    @Test
    fun `should award budget to user successfully`() {
        // Given
        val gameId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("100.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(gameRepository.updateRemainingBudget(gameId, BigDecimal("900.00"))).thenReturn(true)
        whenever(budgetTransactionRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        budgetService.awardToUser(gameId, userId, questionId, amount)

        // Then
        verify(gameRepository).updateRemainingBudget(gameId, BigDecimal("900.00"))
        verify(budgetTransactionRepository).save(any())
        verify(gameMetrics).recordBudgetAwarded(amount, gameId, userId)
    }

    @Test
    fun `should throw InsufficientBudgetException when awarding exceeds budget`() {
        // Given
        val gameId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("1500.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When & Then
        assertThatThrownBy {
            budgetService.awardToUser(gameId, userId, questionId, amount)
        }.isInstanceOf(InsufficientBudgetException::class.java)
    }

    @Test
    fun `should throw GameNotFoundException when game does not exist for award`() {
        // Given
        val gameId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("100.00")

        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            budgetService.awardToUser(gameId, userId, questionId, amount)
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should get remaining budget successfully`() {
        // Given
        val gameId = UUID.randomUUID()
        val expectedBudget = BigDecimal("750.50")
        val game = createGame(gameId, remainingBudget = expectedBudget)

        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When
        val result = budgetService.getRemainingBudget(gameId)

        // Then
        assertThat(result).isEqualByComparingTo(expectedBudget)
    }

    @Test
    fun `should throw GameNotFoundException when getting budget for non-existent game`() {
        // Given
        val gameId = UUID.randomUUID()
        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            budgetService.getRemainingBudget(gameId)
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should handle multiple sequential allocations`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId1 = UUID.randomUUID()
        val questionId2 = UUID.randomUUID()
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(gameRepository.updateRemainingBudget(gameId, BigDecimal("900.00"))).thenReturn(true)
        whenever(gameRepository.updateRemainingBudget(gameId, BigDecimal("800.00"))).thenReturn(true)
        whenever(budgetTransactionRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result1 = budgetService.allocateQuestionReward(gameId, questionId1, BigDecimal("100.00"))

        // Update remaining budget for second allocation
        whenever(gameRepository.findById(gameId)).thenReturn(game.copy(remainingBudget = BigDecimal("900.00")))
        val result2 = budgetService.allocateQuestionReward(gameId, questionId2, BigDecimal("100.00"))

        // Then
        assertThat(result1).isTrue()
        assertThat(result2).isTrue()
        verify(budgetTransactionRepository, times(2)).save(any())
    }

    @Test
    fun `should handle exact budget allocation`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val amount = BigDecimal("1000.00")
        val game = createGame(gameId, remainingBudget = BigDecimal("1000.00"))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(gameRepository.updateRemainingBudget(eq(gameId), any())).thenReturn(true)
        whenever(budgetTransactionRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = budgetService.allocateQuestionReward(gameId, questionId, amount)

        // Then
        assertThat(result).isTrue()
        verify(gameRepository).updateRemainingBudget(eq(gameId), argThat { this.compareTo(BigDecimal.ZERO) == 0 })
    }

    private fun createGame(
        id: UUID,
        remainingBudget: BigDecimal = BigDecimal("1000.00")
    ) = Game(
        id = id,
        name = "Test Game",
        gameType = GameType.MCQ_FIFO,
        initialBudget = BigDecimal("1000.00"),
        remainingBudget = remainingBudget,
        status = GameStatus.ACTIVE,
        scheduledAt = null,
        startedAt = Instant.now(),
        endedAt = null,
        questionTimerSeconds = 30,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}

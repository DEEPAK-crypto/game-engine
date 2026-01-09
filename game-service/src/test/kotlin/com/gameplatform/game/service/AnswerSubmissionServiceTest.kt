package com.gameplatform.game.service

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.ActiveQuestionResult
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionTiming
import com.gameplatform.game.dto.SubmitAnswerRequest
import com.gameplatform.game.exception.*
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.impl.AnswerSubmissionServiceImpl
import com.gameplatform.game.cassandra.repository.TurnRepository
import com.gameplatform.game.cassandra.repository.UserQuestionAnswerRepository
import com.gameplatform.game.metrics.GameMetrics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class AnswerSubmissionServiceTest {

    private lateinit var answerSubmissionService: AnswerSubmissionService
    private lateinit var gameRepository: GameRepository
    private lateinit var questionRepository: QuestionRepository
    private lateinit var turnRepository: TurnRepository
    private lateinit var userQuestionAnswerRepository: UserQuestionAnswerRepository
    private lateinit var budgetService: BudgetService
    private lateinit var gameMetrics: GameMetrics
    private lateinit var redisLeaderboardService: RedisLeaderboardService
    private lateinit var activeQuestionCacheService: ActiveQuestionCacheService
    private lateinit var answerEvaluatorFactory: com.gameplatform.game.service.evaluation.AnswerEvaluatorFactory

    @BeforeEach
    fun setup() {
        gameRepository = mock()
        questionRepository = mock()
        turnRepository = mock()
        userQuestionAnswerRepository = mock()
        budgetService = mock()
        gameMetrics = mock()
        redisLeaderboardService = mock()
        activeQuestionCacheService = mock()
        answerEvaluatorFactory = mock()

        answerSubmissionService = AnswerSubmissionServiceImpl(
            gameRepository,
            questionRepository,
            turnRepository,
            userQuestionAnswerRepository,
            budgetService,
            gameMetrics,
            redisLeaderboardService,
            activeQuestionCacheService,
            answerEvaluatorFactory
        )
    }

    @Test
    fun `should throw GameNotFoundException when game does not exist`() {
        // Given
        val gameId = UUID.randomUUID()
        val request = createSubmitAnswerRequest()
        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            answerSubmissionService.submitAnswer(gameId, request)
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should throw InvalidGameStateException when game is not active`() {
        // Given
        val gameId = UUID.randomUUID()
        val request = createSubmitAnswerRequest()
        val game = createGame(gameId, GameStatus.DRAFT)
        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When & Then
        assertThatThrownBy {
            answerSubmissionService.submitAnswer(gameId, request)
        }.isInstanceOf(InvalidGameStateException::class.java)
    }

    @Test
    fun `should throw NoActiveQuestionException when no questions exist`() {
        // Given
        val gameId = UUID.randomUUID()
        val request = createSubmitAnswerRequest()
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now())
        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(activeQuestionCacheService.getActiveQuestion(eq(gameId), any())).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            answerSubmissionService.submitAnswer(gameId, request)
        }.isInstanceOf(NoActiveQuestionException::class.java)
    }

    @Test
    fun `should throw DuplicateAnswerException when user already answered the question`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val request = createSubmitAnswerRequest()
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(10))
        val question = createQuestion(questionId, gameId, index = 0, durationSeconds = 30)
        val activeQuestionResult = createActiveQuestionResult(questionId, Instant.now().plusSeconds(20))

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(activeQuestionCacheService.getActiveQuestion(eq(gameId), any())).thenReturn(activeQuestionResult)
        whenever(questionRepository.findById(questionId)).thenReturn(question)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(request.userId, gameId, questionId))
            .thenReturn(mock()) // Return existing answer

        // When & Then
        assertThatThrownBy {
            answerSubmissionService.submitAnswer(gameId, request)
        }.isInstanceOf(DuplicateAnswerException::class.java)

        verify(gameMetrics).recordDuplicateAnswer()
    }

    @Test
    fun `should process correct answer and award reward for first place`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val correctOptionId = UUID.randomUUID()
        val request = createSubmitAnswerRequest(selectedOptionId = correctOptionId)
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(5))
        val question = createQuestion(questionId, gameId, index = 0, durationSeconds = 30, correctOptionId = correctOptionId, reward = BigDecimal("100.00"))
        val activeQuestionResult = createActiveQuestionResult(questionId, Instant.now().plusSeconds(25))

        // Mock evaluator
        val mockEvaluator: com.gameplatform.game.service.evaluation.AnswerEvaluator = mock()
        val rewardResult = com.gameplatform.game.service.evaluation.RewardEvaluationResult(
            shouldAwardReward = true,
            rewardAmount = BigDecimal("100.00")
        )
        whenever(answerEvaluatorFactory.getEvaluator(GameType.MCQ_FIFO)).thenReturn(mockEvaluator)
        whenever(mockEvaluator.isAnswerCorrect(any(), any())).thenReturn(true)
        whenever(mockEvaluator.calculateReward(any(), any())).thenReturn(rewardResult)

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(activeQuestionCacheService.getActiveQuestion(eq(gameId), any())).thenReturn(activeQuestionResult)
        whenever(questionRepository.findById(questionId)).thenReturn(question)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(any(), any(), any())).thenReturn(null)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameId(any(), any())).thenReturn(emptyList())
        whenever(redisLeaderboardService.getUserQuestionRank(gameId, questionId, request.userId)).thenReturn(1)
        whenever(turnRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(userQuestionAnswerRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val response = answerSubmissionService.submitAnswer(gameId, request)

        // Then
        assertThat(response.isCorrect).isTrue()
        assertThat(response.rank).isEqualTo(1)
        assertThat(response.rewardAmount).isEqualByComparingTo(BigDecimal("100.00"))

        verify(budgetService).awardToUser(gameId, request.userId, questionId, BigDecimal("100.00"))
        verify(gameMetrics).recordAnswerSubmission(true, gameId, questionId)
        verify(gameMetrics).recordReward(BigDecimal("100.00"), gameId, request.userId)
        verify(redisLeaderboardService, times(2)).addToQuestionLeaderboard(eq(gameId), eq(questionId), eq(request.userId), any(), any())
        verify(redisLeaderboardService).updateGameLeaderboard(eq(gameId), eq(request.userId), any(), any())
    }

    @Test
    fun `should process correct answer without reward for non-first place`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val correctOptionId = UUID.randomUUID()
        val request = createSubmitAnswerRequest(selectedOptionId = correctOptionId)
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(5))
        val question = createQuestion(questionId, gameId, index = 0, durationSeconds = 30, correctOptionId = correctOptionId, reward = BigDecimal("100.00"))
        val activeQuestionResult = createActiveQuestionResult(questionId, Instant.now().plusSeconds(25))

        // Mock evaluator
        val mockEvaluator: com.gameplatform.game.service.evaluation.AnswerEvaluator = mock()
        val rewardResult = com.gameplatform.game.service.evaluation.RewardEvaluationResult(
            shouldAwardReward = false,
            rewardAmount = BigDecimal.ZERO
        )
        whenever(answerEvaluatorFactory.getEvaluator(GameType.MCQ_FIFO)).thenReturn(mockEvaluator)
        whenever(mockEvaluator.isAnswerCorrect(any(), any())).thenReturn(true)
        whenever(mockEvaluator.calculateReward(any(), any())).thenReturn(rewardResult)

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(activeQuestionCacheService.getActiveQuestion(eq(gameId), any())).thenReturn(activeQuestionResult)
        whenever(questionRepository.findById(questionId)).thenReturn(question)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(any(), any(), any())).thenReturn(null)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameId(any(), any())).thenReturn(emptyList())
        whenever(redisLeaderboardService.getUserQuestionRank(gameId, questionId, request.userId)).thenReturn(2) // Second place
        whenever(turnRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(userQuestionAnswerRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val response = answerSubmissionService.submitAnswer(gameId, request)

        // Then
        assertThat(response.isCorrect).isTrue()
        assertThat(response.rank).isEqualTo(2)
        assertThat(response.rewardAmount).isEqualByComparingTo(BigDecimal.ZERO)

        verify(budgetService, never()).awardToUser(any(), any(), any(), any())
        verify(gameMetrics).recordAnswerSubmission(true, gameId, questionId)
        verify(gameMetrics, never()).recordReward(any(), any(), any())
    }

    @Test
    fun `should process incorrect answer without reward`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val correctOptionId = UUID.randomUUID()
        val wrongOptionId = UUID.randomUUID()
        val request = createSubmitAnswerRequest(selectedOptionId = wrongOptionId)
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(5))
        val question = createQuestion(questionId, gameId, index = 0, durationSeconds = 30, correctOptionId = correctOptionId, reward = BigDecimal("100.00"))
        val activeQuestionResult = createActiveQuestionResult(questionId, Instant.now().plusSeconds(25))

        // Mock evaluator
        val mockEvaluator: com.gameplatform.game.service.evaluation.AnswerEvaluator = mock()
        whenever(answerEvaluatorFactory.getEvaluator(GameType.MCQ_FIFO)).thenReturn(mockEvaluator)
        whenever(mockEvaluator.isAnswerCorrect(any(), any())).thenReturn(false)

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(activeQuestionCacheService.getActiveQuestion(eq(gameId), any())).thenReturn(activeQuestionResult)
        whenever(questionRepository.findById(questionId)).thenReturn(question)
        whenever(userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(any(), any(), any())).thenReturn(null)
        whenever(turnRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(userQuestionAnswerRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val response = answerSubmissionService.submitAnswer(gameId, request)

        // Then
        assertThat(response.isCorrect).isFalse()
        assertThat(response.rank).isNull()
        assertThat(response.rewardAmount).isEqualByComparingTo(BigDecimal.ZERO)

        verify(budgetService, never()).awardToUser(any(), any(), any(), any())
        verify(gameMetrics).recordAnswerSubmission(false, gameId, questionId)
        verify(redisLeaderboardService, never()).addToQuestionLeaderboard(any(), any(), any(), any(), any())
    }

    private fun createSubmitAnswerRequest(
        userId: UUID = UUID.randomUUID(),
        selectedOptionId: UUID = UUID.randomUUID(),
        clientTimestamp: Instant = Instant.now()
    ) = SubmitAnswerRequest(
        userId = userId,
        selectedOptionId = selectedOptionId,
        clientTimestamp = clientTimestamp
    )

    private fun createGame(
        id: UUID,
        status: GameStatus,
        startedAt: Instant? = null
    ) = Game(
        id = id,
        name = "Test Game",
        gameType = GameType.MCQ_FIFO,
        initialBudget = BigDecimal("1000.00"),
        remainingBudget = BigDecimal("1000.00"),
        status = status,
        scheduledAt = null,
        startedAt = startedAt,
        endedAt = null,
        questionTimerSeconds = 30,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createQuestion(
        id: UUID,
        gameId: UUID,
        index: Int,
        durationSeconds: Int,
        correctOptionId: UUID = UUID.randomUUID(),
        reward: BigDecimal = BigDecimal("100.00")
    ) = Question(
        id = id,
        gameId = gameId,
        orderIndex = index,
        questionText = "Test Question",
        correctOptionId = correctOptionId,
        reward = reward,
        durationSeconds = durationSeconds,
        createdAt = Instant.now()
    )

    private fun createActiveQuestionResult(
        questionId: UUID,
        expiresAt: Instant,
        questionStartedAt: Instant? = Instant.now().minusSeconds(5)
    ) = ActiveQuestionResult(
        activeQuestion = QuestionTiming(
            questionId = questionId,
            orderIndex = 0,
            durationSeconds = 30
        ),
        expiresAt = expiresAt,
        isGameEnded = false,
        questionStartedAt = questionStartedAt
    )
}

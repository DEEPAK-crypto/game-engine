package com.gameplatform.game.service

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionOption
import com.gameplatform.game.dto.CreateQuestionOptionRequest
import com.gameplatform.game.dto.CreateQuestionRequest
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InvalidQuestionOrderException
import com.gameplatform.game.exception.QuestionNotFoundException
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionOptionRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.impl.QuestionServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class QuestionServiceTest {

    private lateinit var questionService: QuestionService
    private lateinit var questionRepository: QuestionRepository
    private lateinit var questionOptionRepository: QuestionOptionRepository
    private lateinit var gameRepository: GameRepository

    @BeforeEach
    fun setup() {
        questionRepository = mock()
        questionOptionRepository = mock()
        gameRepository = mock()

        questionService = QuestionServiceImpl(
            questionRepository,
            questionOptionRepository,
            gameRepository
        )
    }

    @Test
    fun `should add questions successfully when game exists`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        val request = createQuestionRequest()

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameId(gameId)).thenReturn(emptyList())
        whenever(questionOptionRepository.saveAll(any<List<QuestionOption>>())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(questionRepository.saveAll(any<List<Question>>())).thenAnswer { invocation -> invocation.arguments[0] }

        // When
        val result = questionService.addQuestions(gameId, listOf(request))

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].questionText).isEqualTo("What is 2+2?")
        assertThat(result[0].options).hasSize(4)
        assertThat(result[0].orderIndex).isEqualTo(0)

        verify(questionRepository).saveAll(argThat { list -> list.size == 1 })
        verify(questionOptionRepository).saveAll(argThat { list -> list.size == 4 })
    }

    @Test
    fun `should add multiple questions with correct order indices`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        val existingQuestion = createQuestion(UUID.randomUUID(), gameId, 0)
        val requests = listOf(
            createQuestionRequest("Question 1"),
            createQuestionRequest("Question 2"),
            createQuestionRequest("Question 3")
        )

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameId(gameId)).thenReturn(listOf(existingQuestion))
        whenever(questionOptionRepository.saveAll(any<List<QuestionOption>>())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(questionRepository.saveAll(any<List<Question>>())).thenAnswer { invocation -> invocation.arguments[0] }

        // When
        val result = questionService.addQuestions(gameId, requests)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].orderIndex).isEqualTo(1) // After existing question at index 0
        assertThat(result[1].orderIndex).isEqualTo(2)
        assertThat(result[2].orderIndex).isEqualTo(3)

        verify(questionRepository).saveAll(argThat { list -> list.size == 3 })
        verify(questionOptionRepository).saveAll(argThat { list -> list.size == 12 }) // 3 questions × 4 options
    }

    @Test
    fun `should throw GameNotFoundException when game does not exist`() {
        // Given
        val gameId = UUID.randomUUID()
        val request = createQuestionRequest()
        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            questionService.addQuestions(gameId, listOf(request))
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should throw InvalidQuestionOrderException when less than 2 options`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        val request = CreateQuestionRequest(
            questionText = "Invalid question",
            options = listOf(CreateQuestionOptionRequest("Only one option")),
            correctOptionIndex = 0,
            reward = BigDecimal("100.00"),
            durationSeconds = 30
        )

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameId(gameId)).thenReturn(emptyList())

        // When & Then
        assertThatThrownBy {
            questionService.addQuestions(gameId, listOf(request))
        }.isInstanceOf(InvalidQuestionOrderException::class.java)
            .hasMessageContaining("at least 2 options")
    }

    @Test
    fun `should throw InvalidQuestionOrderException when correct option index is out of bounds`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        val request = CreateQuestionRequest(
            questionText = "Invalid question",
            options = listOf(
                CreateQuestionOptionRequest("Option 1"),
                CreateQuestionOptionRequest("Option 2")
            ),
            correctOptionIndex = 5, // Out of bounds
            reward = BigDecimal("100.00"),
            durationSeconds = 30
        )

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameId(gameId)).thenReturn(emptyList())

        // When & Then
        assertThatThrownBy {
            questionService.addQuestions(gameId, listOf(request))
        }.isInstanceOf(InvalidQuestionOrderException::class.java)
            .hasMessageContaining("Invalid correct option index")
    }

    @Test
    fun `should get question by id successfully`() {
        // Given
        val questionId = UUID.randomUUID()
        val gameId = UUID.randomUUID()
        val question = createQuestion(questionId, gameId, 0)
        val options = createOptions(questionId)

        whenever(questionRepository.findById(questionId)).thenReturn(question)
        whenever(questionOptionRepository.findByQuestionIdOrderByIndex(questionId)).thenReturn(options)

        // When
        val result = questionService.getQuestion(questionId)

        // Then
        assertThat(result.id).isEqualTo(questionId)
        assertThat(result.questionText).isEqualTo("What is 2+2?")
        assertThat(result.options).hasSize(4)
        assertThat(result.reward).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should throw QuestionNotFoundException when question does not exist`() {
        // Given
        val questionId = UUID.randomUUID()
        whenever(questionRepository.findById(questionId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            questionService.getQuestion(questionId)
        }.isInstanceOf(QuestionNotFoundException::class.java)
    }

    @Test
    fun `should get all questions by game id`() {
        // Given
        val gameId = UUID.randomUUID()
        val question1 = createQuestion(UUID.randomUUID(), gameId, 0)
        val question2 = createQuestion(UUID.randomUUID(), gameId, 1)
        val options1 = createOptions(question1.id)
        val options2 = createOptions(question2.id)

        whenever(questionRepository.findByGameIdOrderByIndex(gameId)).thenReturn(listOf(question1, question2))
        whenever(questionOptionRepository.findByQuestionIdOrderByIndex(question1.id)).thenReturn(options1)
        whenever(questionOptionRepository.findByQuestionIdOrderByIndex(question2.id)).thenReturn(options2)

        // When
        val result = questionService.getQuestionsByGame(gameId)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].orderIndex).isEqualTo(0)
        assertThat(result[1].orderIndex).isEqualTo(1)
    }

    @Test
    fun `should return empty list when game has no questions`() {
        // Given
        val gameId = UUID.randomUUID()
        whenever(questionRepository.findByGameIdOrderByIndex(gameId)).thenReturn(emptyList())

        // When
        val result = questionService.getQuestionsByGame(gameId)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should get active question when game is running`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(5))
        val question = createQuestion(questionId, gameId, 0, durationSeconds = 30)
        val options = createOptions(questionId)

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameIdOrderByIndex(gameId)).thenReturn(listOf(question))
        whenever(questionOptionRepository.findByQuestionIdOrderByIndex(questionId)).thenReturn(options)

        // When
        val result = questionService.getActiveQuestion(gameId)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.question.id).isEqualTo(questionId)
        assertThat(result.remainingSeconds).isGreaterThan(0)
        assertThat(result.startTime).isNotNull()
        assertThat(result.endTime).isNotNull()
    }

    @Test
    fun `should throw GameNotFoundException when getting active question for non-existent game`() {
        // Given
        val gameId = UUID.randomUUID()
        whenever(gameRepository.findById(gameId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            questionService.getActiveQuestion(gameId)
        }.isInstanceOf(GameNotFoundException::class.java)
    }

    @Test
    fun `should return null when game is not active`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When
        val result = questionService.getActiveQuestion(gameId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null when game has no started time`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = null)
        whenever(gameRepository.findById(gameId)).thenReturn(game)

        // When
        val result = questionService.getActiveQuestion(gameId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null when game has no questions`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now())
        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameIdOrderByIndex(gameId)).thenReturn(emptyList())

        // When
        val result = questionService.getActiveQuestion(gameId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null when no question is currently active`() {
        // Given
        val gameId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        // Game started 2 minutes ago, question duration is 30 seconds, so it has expired
        val game = createGame(gameId, GameStatus.ACTIVE, startedAt = Instant.now().minusSeconds(120))
        val question = createQuestion(questionId, gameId, 0, durationSeconds = 30)

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameIdOrderByIndex(gameId)).thenReturn(listOf(question))

        // When
        val result = questionService.getActiveQuestion(gameId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should delete question and its options successfully`() {
        // Given
        val questionId = UUID.randomUUID()
        val gameId = UUID.randomUUID()
        val question = createQuestion(questionId, gameId, 0)

        whenever(questionRepository.findById(questionId)).thenReturn(question)

        // When
        questionService.deleteQuestion(questionId)

        // Then
        verify(questionOptionRepository).deleteByQuestionId(questionId)
        verify(questionRepository).delete(questionId)
    }

    @Test
    fun `should throw QuestionNotFoundException when deleting non-existent question`() {
        // Given
        val questionId = UUID.randomUUID()
        whenever(questionRepository.findById(questionId)).thenReturn(null)

        // When & Then
        assertThatThrownBy {
            questionService.deleteQuestion(questionId)
        }.isInstanceOf(QuestionNotFoundException::class.java)

        verify(questionOptionRepository, never()).deleteByQuestionId(any())
        verify(questionRepository, never()).delete(any())
    }

    @Test
    fun `should preserve correct option id when adding questions`() {
        // Given
        val gameId = UUID.randomUUID()
        val game = createGame(gameId, GameStatus.DRAFT)
        val request = CreateQuestionRequest(
            questionText = "What is the capital of France?",
            options = listOf(
                CreateQuestionOptionRequest("London"),
                CreateQuestionOptionRequest("Paris"),
                CreateQuestionOptionRequest("Berlin"),
                CreateQuestionOptionRequest("Madrid")
            ),
            correctOptionIndex = 1, // Paris
            reward = BigDecimal("100.00"),
            durationSeconds = 30
        )

        whenever(gameRepository.findById(gameId)).thenReturn(game)
        whenever(questionRepository.findByGameId(gameId)).thenReturn(emptyList())
        whenever(questionOptionRepository.saveAll(any<List<QuestionOption>>())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(questionRepository.saveAll(any<List<Question>>())).thenAnswer { invocation -> invocation.arguments[0] }

        // When
        val result = questionService.addQuestions(gameId, listOf(request))

        // Then
        assertThat(result).hasSize(1)
        val savedQuestion = result[0]
        val correctOption = savedQuestion.options.find { it.orderIndex == 1 }
        assertThat(correctOption).isNotNull
        assertThat(correctOption!!.optionText).isEqualTo("Paris")
    }

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
        orderIndex: Int,
        durationSeconds: Int = 30
    ) = Question(
        id = id,
        gameId = gameId,
        questionText = "What is 2+2?",
        orderIndex = orderIndex,
        correctOptionId = UUID.randomUUID(),
        reward = BigDecimal("100.00"),
        durationSeconds = durationSeconds,
        createdAt = Instant.now()
    )

    private fun createOptions(questionId: UUID): List<QuestionOption> {
        return listOf(
            QuestionOption(UUID.randomUUID(), questionId, "3", 0, Instant.now()),
            QuestionOption(UUID.randomUUID(), questionId, "4", 1, Instant.now()),
            QuestionOption(UUID.randomUUID(), questionId, "5", 2, Instant.now()),
            QuestionOption(UUID.randomUUID(), questionId, "6", 3, Instant.now())
        )
    }

    private fun createQuestionRequest(questionText: String = "What is 2+2?") = CreateQuestionRequest(
        questionText = questionText,
        options = listOf(
            CreateQuestionOptionRequest("3"),
            CreateQuestionOptionRequest("4"),
            CreateQuestionOptionRequest("5"),
            CreateQuestionOptionRequest("6")
        ),
        correctOptionIndex = 1,
        reward = BigDecimal("100.00"),
        durationSeconds = 30
    )
}

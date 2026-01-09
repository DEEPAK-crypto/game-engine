package com.gameplatform.game.domain.calculator

import com.gameplatform.game.domain.model.QuestionTiming
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

class ActiveQuestionCalculatorTest {

    private val gameStart = Instant.parse("2024-01-01T10:00:00Z")

    private val questions = listOf(
        QuestionTiming(UUID.randomUUID(), 0, 30), // Q1: 0-30s
        QuestionTiming(UUID.randomUUID(), 1, 30), // Q2: 30-60s
        QuestionTiming(UUID.randomUUID(), 2, 30)  // Q3: 60-90s
    )

    @Test
    fun `calculate returns first question at game start`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart
        )

        assertNotNull(result.activeQuestion)
        assertEquals(questions[0].questionId, result.activeQuestion?.questionId)
        assertEquals(0, result.activeQuestion?.orderIndex)
        assertEquals(gameStart.plusSeconds(30), result.expiresAt)
        assertFalse(result.isGameEnded)
    }

    @Test
    fun `calculate returns correct question at 15 seconds`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(15)
        )

        assertEquals(questions[0].questionId, result.activeQuestion?.questionId)
        assertEquals(gameStart.plusSeconds(30), result.expiresAt)
        assertFalse(result.isGameEnded)
    }

    @Test
    fun `calculate returns second question at 45 seconds`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(45)
        )

        assertEquals(questions[1].questionId, result.activeQuestion?.questionId)
        assertEquals(1, result.activeQuestion?.orderIndex)
        assertEquals(gameStart.plusSeconds(60), result.expiresAt)
        assertFalse(result.isGameEnded)
    }

    @Test
    fun `calculate returns third question at 75 seconds`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(75)
        )

        assertEquals(questions[2].questionId, result.activeQuestion?.questionId)
        assertEquals(2, result.activeQuestion?.orderIndex)
        assertEquals(gameStart.plusSeconds(90), result.expiresAt)
        assertFalse(result.isGameEnded)
    }

    @Test
    fun `calculate returns game ended after all questions`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(100)
        )

        assertNull(result.activeQuestion)
        assertNull(result.expiresAt)
        assertTrue(result.isGameEnded)
    }

    @Test
    fun `calculate returns game ended exactly at last question expiry`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(90)
        )

        assertNull(result.activeQuestion)
        assertTrue(result.isGameEnded)
    }

    @Test
    fun `calculate handles empty questions list`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = emptyList(),
            currentTime = gameStart
        )

        assertNull(result.activeQuestion)
        assertTrue(result.isGameEnded)
    }

    @Test
    fun `calculate handles time before game start`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.minusSeconds(10)
        )

        assertNotNull(result.activeQuestion)
        assertEquals(questions[0].questionId, result.activeQuestion?.questionId)
        assertFalse(result.isGameEnded)
    }

    @Test
    fun `calculate handles questions with different durations`() {
        val variableQuestions = listOf(
            QuestionTiming(UUID.randomUUID(), 0, 20), // 0-20s
            QuestionTiming(UUID.randomUUID(), 1, 40), // 20-60s
            QuestionTiming(UUID.randomUUID(), 2, 10)  // 60-70s
        )

        // At 50 seconds, should be Q2
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = variableQuestions,
            currentTime = gameStart.plusSeconds(50)
        )

        assertEquals(variableQuestions[1].questionId, result.activeQuestion?.questionId)
        assertEquals(gameStart.plusSeconds(60), result.expiresAt)
    }

    @Test
    fun `calculate correctly sorts questions by order index`() {
        // Questions in wrong order
        val unsortedQuestions = listOf(
            QuestionTiming(UUID.randomUUID(), 2, 30),
            QuestionTiming(UUID.randomUUID(), 0, 30),
            QuestionTiming(UUID.randomUUID(), 1, 30)
        )

        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = unsortedQuestions,
            currentTime = gameStart.plusSeconds(5)
        )

        // Should return the question with orderIndex 0
        assertEquals(0, result.activeQuestion?.orderIndex)
    }

    @Test
    fun `isQuestionStillActive returns true for active question`() {
        val isActive = ActiveQuestionCalculator.isQuestionStillActive(
            gameStartedAt = gameStart,
            questions = questions,
            questionId = questions[0].questionId,
            submissionTime = gameStart.plusSeconds(15)
        )

        assertTrue(isActive)
    }

    @Test
    fun `isQuestionStillActive returns false for expired question`() {
        val isActive = ActiveQuestionCalculator.isQuestionStillActive(
            gameStartedAt = gameStart,
            questions = questions,
            questionId = questions[0].questionId,
            submissionTime = gameStart.plusSeconds(45)
        )

        assertFalse(isActive)
    }

    @Test
    fun `isQuestionStillActive returns false for future question`() {
        val isActive = ActiveQuestionCalculator.isQuestionStillActive(
            gameStartedAt = gameStart,
            questions = questions,
            questionId = questions[2].questionId,
            submissionTime = gameStart.plusSeconds(15)
        )

        assertFalse(isActive)
    }

    @Test
    fun `getQuestionAtTime returns correct question`() {
        val question = ActiveQuestionCalculator.getQuestionAtTime(
            gameStartedAt = gameStart,
            questions = questions,
            targetTime = gameStart.plusSeconds(45)
        )

        assertEquals(questions[1].questionId, question?.questionId)
    }

    @Test
    fun `getQuestionTimeWindow returns correct time range`() {
        val timeWindow = ActiveQuestionCalculator.getQuestionTimeWindow(
            gameStartedAt = gameStart,
            questions = questions,
            questionId = questions[1].questionId
        )

        assertNotNull(timeWindow)
        assertEquals(gameStart.plusSeconds(30), timeWindow?.first)
        assertEquals(gameStart.plusSeconds(60), timeWindow?.second)
    }

    @Test
    fun `getQuestionTimeWindow returns null for unknown question`() {
        val timeWindow = ActiveQuestionCalculator.getQuestionTimeWindow(
            gameStartedAt = gameStart,
            questions = questions,
            questionId = UUID.randomUUID()
        )

        assertNull(timeWindow)
    }

    @Test
    fun `getTotalGameDuration calculates correct total`() {
        val duration = ActiveQuestionCalculator.getTotalGameDuration(questions)

        assertEquals(90L, duration)
    }

    @Test
    fun `calculate provides questionStartedAt for active question`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(45)
        )

        assertEquals(gameStart.plusSeconds(30), result.questionStartedAt)
    }

    @Test
    fun `getRemainingSeconds returns correct value`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(15)
        )

        val remaining = result.getRemainingSeconds(gameStart.plusSeconds(20))

        assertEquals(10L, remaining)
    }

    @Test
    fun `hasActiveQuestion returns true when question is active`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(15)
        )

        assertTrue(result.hasActiveQuestion())
    }

    @Test
    fun `hasActiveQuestion returns false when game ended`() {
        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = gameStart,
            questions = questions,
            currentTime = gameStart.plusSeconds(100)
        )

        assertFalse(result.hasActiveQuestion())
    }
}

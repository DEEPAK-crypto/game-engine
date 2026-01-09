package com.gameplatform.game.testutil

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.domain.model.Game
import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionOption
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.dto.CreateQuestionOptionRequest
import com.gameplatform.game.dto.CreateQuestionRequest
import com.gameplatform.game.dto.SubmitAnswerRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object TestDataFactory {

    fun createGame(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Game",
        gameType: GameType = GameType.MCQ_FIFO,
        initialBudget: BigDecimal = BigDecimal("1000.00"),
        remainingBudget: BigDecimal = initialBudget,
        status: GameStatus = GameStatus.DRAFT,
        scheduledAt: Instant? = null,
        startedAt: Instant? = null,
        endedAt: Instant? = null,
        questionTimerSeconds: Int = 30,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt
    ): Game {
        return Game(
            id = id,
            name = name,
            gameType = gameType,
            initialBudget = initialBudget,
            remainingBudget = remainingBudget,
            status = status,
            scheduledAt = scheduledAt,
            startedAt = startedAt,
            endedAt = endedAt,
            questionTimerSeconds = questionTimerSeconds,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createQuestion(
        id: UUID = UUID.randomUUID(),
        gameId: UUID,
        questionText: String = "What is 2 + 2?",
        orderIndex: Int = 0,
        correctOptionId: UUID? = null,
        reward: BigDecimal = BigDecimal("100.00"),
        durationSeconds: Int = 30,
        createdAt: Instant = Instant.now()
    ): Question {
        return Question(
            id = id,
            gameId = gameId,
            questionText = questionText,
            orderIndex = orderIndex,
            correctOptionId = correctOptionId,
            reward = reward,
            durationSeconds = durationSeconds,
            createdAt = createdAt
        )
    }

    fun createQuestionOption(
        id: UUID = UUID.randomUUID(),
        questionId: UUID,
        optionText: String,
        orderIndex: Int,
        createdAt: Instant = Instant.now()
    ): QuestionOption {
        return QuestionOption(
            id = id,
            questionId = questionId,
            optionText = optionText,
            orderIndex = orderIndex,
            createdAt = createdAt
        )
    }

    fun createGameRequest(
        name: String = "Test Game",
        gameType: GameType = GameType.MCQ_FIFO,
        initialBudget: BigDecimal = BigDecimal("1000.00"),
        questionTimerSeconds: Int = 30,
        scheduledAt: Instant? = null
    ): CreateGameRequest {
        return CreateGameRequest(
            name = name,
            gameType = gameType,
            initialBudget = initialBudget,
            questionTimerSeconds = questionTimerSeconds,
            scheduledAt = scheduledAt
        )
    }

    fun createQuestionRequest(
        questionText: String = "What is the capital of France?",
        options: List<String> = listOf("Paris", "London", "Berlin", "Madrid"),
        correctOptionIndex: Int = 0,
        reward: BigDecimal = BigDecimal("100.00"),
        durationSeconds: Int = 30
    ): CreateQuestionRequest {
        return CreateQuestionRequest(
            questionText = questionText,
            options = options.map { CreateQuestionOptionRequest(it) },
            correctOptionIndex = correctOptionIndex,
            reward = reward,
            durationSeconds = durationSeconds
        )
    }

    fun createSubmitAnswerRequest(
        userId: UUID = UUID.randomUUID(),
        selectedOptionId: UUID,
        clientTimestamp: Instant = Instant.now()
    ): SubmitAnswerRequest {
        return SubmitAnswerRequest(
            userId = userId,
            selectedOptionId = selectedOptionId,
            clientTimestamp = clientTimestamp
        )
    }
}
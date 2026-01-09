package com.gameplatform.game.service.impl

import com.gameplatform.game.domain.calculator.ActiveQuestionCalculator
import com.gameplatform.game.domain.model.ActiveQuestionResult
import com.gameplatform.game.domain.model.Question
import com.gameplatform.game.domain.model.QuestionOption
import com.gameplatform.game.dto.ActiveQuestionResponse
import com.gameplatform.game.dto.CreateQuestionRequest
import com.gameplatform.game.dto.QuestionResponse
import com.gameplatform.game.exception.GameNotFoundException
import com.gameplatform.game.exception.InvalidQuestionOrderException
import com.gameplatform.game.exception.QuestionNotFoundException
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionOptionRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.QuestionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class QuestionServiceImpl(
    private val questionRepository: QuestionRepository,
    private val questionOptionRepository: QuestionOptionRepository,
    private val gameRepository: GameRepository
) : QuestionService {

    override fun addQuestions(gameId: UUID, requests: List<CreateQuestionRequest>): List<QuestionResponse> {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        val existingQuestions = questionRepository.findByGameId(gameId)
        val startIndex = existingQuestions.size

        val now = Instant.now()
        val questions = mutableListOf<Question>()
        val allOptions = mutableListOf<QuestionOption>()

        requests.forEachIndexed { index, request ->
            if (request.options.size < 2) {
                throw InvalidQuestionOrderException("Question must have at least 2 options")
            }

            if (request.correctOptionIndex < 0 || request.correctOptionIndex >= request.options.size) {
                throw InvalidQuestionOrderException("Invalid correct option index: ${request.correctOptionIndex}")
            }

            val questionId = UUID.randomUUID()
            val options = request.options.mapIndexed { optIndex, optReq ->
                QuestionOption(
                    id = UUID.randomUUID(),
                    questionId = questionId,
                    optionText = optReq.optionText,
                    orderIndex = optIndex,
                    createdAt = now
                )
            }

            val correctOptionId = options[request.correctOptionIndex].id

            val question = Question(
                id = questionId,
                gameId = gameId,
                questionText = request.questionText,
                orderIndex = startIndex + index,
                correctOptionId = correctOptionId,
                reward = request.reward,
                durationSeconds = request.durationSeconds,
                createdAt = now
            )

            questions.add(question)
            allOptions.addAll(options)
        }

        questionRepository.saveAll(questions)
        questionOptionRepository.saveAll(allOptions)

        return questions.map { question ->
            val options = allOptions.filter { it.questionId == question.id }
            QuestionResponse.from(question, options)
        }
    }

    @Transactional(readOnly = true)
    override fun getQuestion(questionId: UUID): QuestionResponse {
        val question = questionRepository.findById(questionId)
            ?: throw QuestionNotFoundException(questionId)
        val options = questionOptionRepository.findByQuestionIdOrderByIndex(questionId)
        return QuestionResponse.from(question, options)
    }

    @Transactional(readOnly = true)
    override fun getQuestionsByGame(gameId: UUID): List<QuestionResponse> {
        val questions = questionRepository.findByGameIdOrderByIndex(gameId)
        return questions.map { question ->
            val options = questionOptionRepository.findByQuestionIdOrderByIndex(question.id)
            QuestionResponse.from(question, options)
        }
    }

    @Transactional(readOnly = true)
    override fun getActiveQuestion(gameId: UUID): ActiveQuestionResponse? {
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        if (!game.isActive()) {
            return null
        }

        val startedAt = game.startedAt ?: return null
        val questions = questionRepository.findByGameIdOrderByIndex(gameId)

        if (questions.isEmpty()) {
            return null
        }

        val questionTimings = questions.map { com.gameplatform.game.domain.model.QuestionTiming.from(it) }
        val currentTime = Instant.now()

        val result = ActiveQuestionCalculator.calculate(
            gameStartedAt = startedAt,
            questions = questionTimings,
            currentTime = currentTime
        )

        if (!result.hasActiveQuestion()) {
            return null
        }

        val activeQuestionTiming = result.activeQuestion!!
        val activeQuestion = questions.first { it.id == activeQuestionTiming.questionId }
        val options = questionOptionRepository.findByQuestionIdOrderByIndex(activeQuestion.id)

        return ActiveQuestionResponse(
            question = QuestionResponse.from(activeQuestion, options),
            startTime = result.questionStartedAt!!,
            endTime = result.expiresAt!!,
            remainingSeconds = result.getRemainingSeconds(currentTime) ?: 0
        )
    }

    override fun deleteQuestion(questionId: UUID) {
        val exists = questionRepository.findById(questionId) != null
        if (!exists) {
            throw QuestionNotFoundException(questionId)
        }
        questionOptionRepository.deleteByQuestionId(questionId)
        questionRepository.delete(questionId)
    }
}
package com.gameplatform.game.service.impl

import com.gameplatform.game.cassandra.entity.QuestionLeaderboard
import com.gameplatform.game.cassandra.entity.Turn
import com.gameplatform.game.cassandra.entity.UserQuestionAnswer
import com.gameplatform.game.cassandra.repository.QuestionLeaderboardRepository
import com.gameplatform.game.cassandra.repository.TurnRepository
import com.gameplatform.game.cassandra.repository.UserQuestionAnswerRepository
import com.gameplatform.game.domain.calculator.ActiveQuestionCalculator
import com.gameplatform.game.domain.model.ActiveQuestionResult
import com.gameplatform.game.dto.AnswerSubmissionResponse
import com.gameplatform.game.dto.SubmitAnswerRequest
import com.gameplatform.game.exception.*
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.AnswerSubmissionService
import com.gameplatform.game.service.BudgetService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Service
class AnswerSubmissionServiceImpl(
    private val gameRepository: GameRepository,
    private val questionRepository: QuestionRepository,
    private val turnRepository: TurnRepository,
    private val userQuestionAnswerRepository: UserQuestionAnswerRepository,
    private val questionLeaderboardRepository: QuestionLeaderboardRepository,
    private val budgetService: BudgetService
) : AnswerSubmissionService {

    private val serverSequence = AtomicLong(0)

    @Transactional
    override fun submitAnswer(gameId: UUID, request: SubmitAnswerRequest): AnswerSubmissionResponse {
        val serverTimestamp = Instant.now()
        val sequence = serverSequence.incrementAndGet()

        // 1. Validate game is active
        val game = gameRepository.findById(gameId)
            ?: throw GameNotFoundException(gameId)

        if (!game.isActive()) {
            throw InvalidGameStateException("Game $gameId is not active")
        }

        // 2. Get active question
        val startedAt = game.startedAt
            ?: throw InvalidGameStateException("Game $gameId has no start time")

        val questions = questionRepository.findByGameIdOrderByIndex(gameId)
        if (questions.isEmpty()) {
            throw NoActiveQuestionException(gameId)
        }

        val questionTimings = questions.map { com.gameplatform.game.domain.model.QuestionTiming.from(it) }
        val activeResult = ActiveQuestionCalculator.calculate(startedAt, questionTimings, serverTimestamp)

        if (!activeResult.hasActiveQuestion()) {
            throw NoActiveQuestionException(gameId)
        }

        val activeQuestionTiming = activeResult.activeQuestion!!
        val activeQuestion = questions.first { it.id == activeQuestionTiming.questionId }

        // 3. Check if answer is within time window
        if (serverTimestamp > activeResult.expiresAt!!) {
            throw AnswerSubmissionClosedException(activeQuestion.id)
        }

        // 4. Check for duplicate answer
        val existingAnswer = userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(
            request.userId,
            gameId,
            activeQuestion.id
        )
        if (existingAnswer != null) {
            throw DuplicateAnswerException(request.userId, activeQuestion.id)
        }

        // 5. Validate option belongs to this question
        // Note: In a production system, we'd fetch and validate the option
        // For now, we assume the client sends valid option IDs

        // 6. Check if answer is correct
        val isCorrect = activeQuestion.correctOptionId == request.selectedOptionId

        // 7. Determine reward amount
        var rewardAmount = BigDecimal.ZERO
        var rank: Int? = null

        if (isCorrect) {
            // Get current leaderboard to determine rank
            val currentLeaderboard = questionLeaderboardRepository
                .findByGameIdAndQuestionIdOrderByRank(gameId, activeQuestion.id)

            rank = currentLeaderboard.size + 1

            // First correct answer gets the reward
            if (rank == 1) {
                rewardAmount = activeQuestion.reward
                budgetService.awardToUser(gameId, request.userId, activeQuestion.id, rewardAmount)
            }

            // Update question leaderboard
            val leaderboardEntry = QuestionLeaderboard(
                gameId = gameId,
                questionId = activeQuestion.id,
                rank = rank,
                userId = request.userId,
                rewardAmount = rewardAmount,
                answeredAt = serverTimestamp
            )
            questionLeaderboardRepository.save(leaderboardEntry)
        }

        // 8. Save turn (FIFO ordering)
        val turnId = UUID.randomUUID()
        val turn = Turn(
            gameId = gameId,
            questionId = activeQuestion.id,
            clientTimestamp = request.clientTimestamp,
            serverSequence = sequence,
            turnId = turnId,
            userId = request.userId,
            selectedOptionId = request.selectedOptionId,
            isCorrect = isCorrect,
            rewardAmount = rewardAmount,
            serverTimestamp = serverTimestamp
        )
        turnRepository.save(turn)

        // 9. Save user question answer
        val userAnswer = UserQuestionAnswer(
            userId = request.userId,
            gameId = gameId,
            questionId = activeQuestion.id,
            turnId = turnId,
            selectedOptionId = request.selectedOptionId,
            isCorrect = isCorrect,
            rewardAmount = rewardAmount,
            answeredAt = serverTimestamp
        )
        userQuestionAnswerRepository.save(userAnswer)

        return AnswerSubmissionResponse(
            turnId = turnId,
            userId = request.userId,
            questionId = activeQuestion.id,
            selectedOptionId = request.selectedOptionId,
            isCorrect = isCorrect,
            rewardAmount = rewardAmount,
            rank = rank,
            submittedAt = serverTimestamp
        )
    }
}
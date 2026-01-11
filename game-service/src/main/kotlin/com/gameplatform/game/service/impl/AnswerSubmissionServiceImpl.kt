package com.gameplatform.game.service.impl

import com.gameplatform.game.cassandra.entity.Turn
import com.gameplatform.game.cassandra.entity.UserQuestionAnswer
import com.gameplatform.game.cassandra.repository.TurnRepository
import com.gameplatform.game.cassandra.repository.UserQuestionAnswerRepository
import com.gameplatform.game.dto.AnswerSubmissionResponse
import com.gameplatform.game.dto.SubmitAnswerRequest
import com.gameplatform.game.exception.*
import com.gameplatform.game.metrics.GameMetrics
import com.gameplatform.game.repository.GameRepository
import com.gameplatform.game.repository.QuestionRepository
import com.gameplatform.game.service.AnswerSubmissionService
import com.gameplatform.game.service.BudgetService
import com.gameplatform.game.service.RedisLeaderboardService
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class AnswerSubmissionServiceImpl(
    private val gameRepository: GameRepository,
    private val questionRepository: QuestionRepository,
    private val turnRepository: TurnRepository,
    private val userQuestionAnswerRepository: UserQuestionAnswerRepository,
    private val budgetService: BudgetService,
    private val gameMetrics: GameMetrics,
    private val redisLeaderboardService: RedisLeaderboardService,
    private val activeQuestionCacheService: com.gameplatform.game.service.ActiveQuestionCacheService,
    private val answerEvaluatorFactory: com.gameplatform.game.service.evaluation.AnswerEvaluatorFactory
) : AnswerSubmissionService {

    private val log = LoggerFactory.getLogger(AnswerSubmissionServiceImpl::class.java)

    @Transactional
    override fun submitAnswer(gameId: UUID, request: SubmitAnswerRequest): AnswerSubmissionResponse {
        log.info(
            "Received answer submission",
            kv("gameId", gameId),
            kv("userId", request.userId),
            kv("selectedOptionId", request.selectedOptionId),
            kv("clientTimestamp", request.clientTimestamp)
        )

        val serverTimestamp = Instant.now()
        // Use nanosecond time for server sequence - unique across all instances
        // This is only used for tie-breaking when client_timestamp is identical
        val sequence = System.nanoTime()

        // 1. Validate game is active
        val game = gameRepository.findById(gameId)
            ?: run {
                log.warn("Game not found", kv("gameId", gameId))
                throw GameNotFoundException(gameId)
            }

        if (!game.isActive()) {
            log.warn(
                "Game is not active",
                kv("gameId", gameId),
                kv("gameStatus", game.status)
            )
            throw InvalidGameStateException("Game $gameId is not active")
        }

        // 2. Get active question (cached)
        val activeResult = activeQuestionCacheService.getActiveQuestion(gameId, serverTimestamp)
            ?: throw NoActiveQuestionException(gameId)

        if (!activeResult.hasActiveQuestion()) {
            throw NoActiveQuestionException(gameId)
        }

        val activeQuestionTiming = activeResult.activeQuestion!!

        // Get full question details (we still need this for correctness check and reward)
        val activeQuestion = questionRepository.findById(activeQuestionTiming.questionId)
            ?: throw NoActiveQuestionException(gameId)

        // 3. Check if answer is within time window
        if (serverTimestamp > activeResult.expiresAt!!) {
            log.warn(
                "Answer submission after question expired",
                kv("gameId", gameId),
                kv("userId", request.userId),
                kv("questionId", activeQuestion.id),
                kv("expiresAt", activeResult.expiresAt),
                kv("serverTimestamp", serverTimestamp)
            )
            gameMetrics.recordLateAnswer()
            throw AnswerSubmissionClosedException(activeQuestion.id)
        }

        // 4. Check for duplicate answer
        val existingAnswer = userQuestionAnswerRepository.findByUserIdAndGameIdAndQuestionId(
            request.userId,
            gameId,
            activeQuestion.id
        )
        if (existingAnswer != null) {
            log.warn(
                "Duplicate answer submission attempt",
                kv("gameId", gameId),
                kv("userId", request.userId),
                kv("questionId", activeQuestion.id)
            )
            gameMetrics.recordDuplicateAnswer()
            throw DuplicateAnswerException(request.userId, activeQuestion.id)
        }

        // 5. Validate option belongs to this question
        // Note: In a production system, we'd fetch and validate the option
        // For now, we assume the client sends valid option IDs

        // 6. Get the appropriate evaluator for this game type
        val evaluator = answerEvaluatorFactory.getEvaluator(game.gameType)

        log.debug(
            "Using evaluator for game type",
            kv("gameId", gameId),
            kv("gameType", game.gameType),
            kv("evaluatorClass", evaluator::class.simpleName)
        )

        // 7. Evaluate answer for correctness
        val isCorrect = evaluator.isAnswerCorrect(activeQuestion, request)

        var rewardAmount = BigDecimal.ZERO
        var rank: Int? = null

        // 8. Only add correct answers to leaderboard and determine reward
        if (isCorrect) {
            // Get max winners from evaluator (for MCQ_FIFO this is 1)
            val maxWinners = evaluator.getMaxWinners()

            // Atomically add to leaderboard and claim winner slot
            // This prevents race conditions where two users could both see rank 1
            val claimResult = redisLeaderboardService.addToLeaderboardAndClaimWinnerSlot(
                gameId = gameId,
                questionId = activeQuestion.id,
                userId = request.userId,
                answeredAt = serverTimestamp,
                maxWinners = maxWinners
            )

            rank = claimResult.rank
            val shouldAwardReward = claimResult.claimedWinnerSlot

            // Calculate reward amount (only non-zero if we claimed a winner slot)
            rewardAmount = if (shouldAwardReward) activeQuestion.reward else BigDecimal.ZERO

            log.debug(
                "Correct answer received (atomic claim)",
                kv("gameId", gameId),
                kv("userId", request.userId),
                kv("questionId", activeQuestion.id),
                kv("rank", rank),
                kv("claimedWinnerSlot", shouldAwardReward),
                kv("currentWinnerCount", claimResult.currentWinnerCount),
                kv("rewardAmount", rewardAmount)
            )

            // Award reward if winner slot was claimed atomically
            if (shouldAwardReward) {
                budgetService.awardToUser(gameId, request.userId, activeQuestion.id, rewardAmount)

                log.info(
                    "Reward awarded (atomic winner slot claimed)",
                    kv("gameId", gameId),
                    kv("userId", request.userId),
                    kv("questionId", activeQuestion.id),
                    kv("gameType", game.gameType),
                    kv("rank", rank),
                    kv("rewardAmount", rewardAmount)
                )

                gameMetrics.recordReward(rewardAmount, gameId, request.userId)

                // Update leaderboard with actual reward amount
                redisLeaderboardService.addToQuestionLeaderboard(
                    gameId = gameId,
                    questionId = activeQuestion.id,
                    userId = request.userId,
                    rewardAmount = rewardAmount,
                    answeredAt = serverTimestamp
                )
            }

            // Update user's total reward in game leaderboard
            val userTotalReward = userQuestionAnswerRepository
                .findByUserIdAndGameId(request.userId, gameId)
                .sumOf { it.rewardAmount } + rewardAmount

            redisLeaderboardService.updateGameLeaderboard(
                gameId = gameId,
                userId = request.userId,
                totalReward = userTotalReward,
                lastUpdated = serverTimestamp
            )
        }

        // 9. Record metrics for answer submission
        gameMetrics.recordAnswerSubmission(isCorrect, gameId, activeQuestion.id)

        log.debug(
            "Answer evaluation completed",
            kv("gameId", gameId),
            kv("userId", request.userId),
            kv("questionId", activeQuestion.id),
            kv("gameType", game.gameType),
            kv("isCorrect", isCorrect),
            kv("rank", rank),
            kv("rewardAmount", rewardAmount)
        )

        // 10. Save turn (FIFO ordering)
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

        // 12. Save user question answer
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

        log.info(
            "Answer submission processed successfully",
            kv("gameId", gameId),
            kv("userId", request.userId),
            kv("questionId", activeQuestion.id),
            kv("turnId", turnId),
            kv("isCorrect", isCorrect),
            kv("rank", rank),
            kv("rewardAmount", rewardAmount)
        )

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
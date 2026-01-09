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
    private val activeQuestionCacheService: com.gameplatform.game.service.ActiveQuestionCacheService
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

        // 6. Check if answer is correct
        val isCorrect = activeQuestion.correctOptionId == request.selectedOptionId

        log.debug(
            "Answer correctness determined",
            kv("gameId", gameId),
            kv("userId", request.userId),
            kv("questionId", activeQuestion.id),
            kv("isCorrect", isCorrect)
        )

        // Record metrics for answer submission
        gameMetrics.recordAnswerSubmission(isCorrect, gameId, activeQuestion.id)

        // 7. Determine reward amount
        var rewardAmount = BigDecimal.ZERO
        var rank: Int? = null

        if (isCorrect) {
            // First correct answer gets the reward
            // We need to add to leaderboard before checking rank to ensure atomicity
            redisLeaderboardService.addToQuestionLeaderboard(
                gameId = gameId,
                questionId = activeQuestion.id,
                userId = request.userId,
                rewardAmount = BigDecimal.ZERO, // Add to leaderboard first with zero reward
                answeredAt = serverTimestamp
            )

            // Get user's rank after adding to leaderboard
            rank = redisLeaderboardService.getUserQuestionRank(gameId, activeQuestion.id, request.userId)

            log.debug(
                "Correct answer received",
                kv("gameId", gameId),
                kv("userId", request.userId),
                kv("questionId", activeQuestion.id),
                kv("rank", rank)
            )

            // Award reward to first correct answer
            if (rank == 1) {
                rewardAmount = activeQuestion.reward
                budgetService.awardToUser(gameId, request.userId, activeQuestion.id, rewardAmount)

                log.info(
                    "Reward awarded for first correct answer",
                    kv("gameId", gameId),
                    kv("userId", request.userId),
                    kv("questionId", activeQuestion.id),
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
            // Get user's current total reward
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
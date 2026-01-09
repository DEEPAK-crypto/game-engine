package com.gameplatform.game.domain.calculator

import com.gameplatform.game.domain.model.ActiveQuestionResult
import com.gameplatform.game.domain.model.QuestionTiming
import java.time.Duration
import java.time.Instant
import java.util.UUID

object ActiveQuestionCalculator {

    /**
     * Calculates the currently active question based on game start time and question durations.
     *
     * This is a pure function that enables stateless, distributed-safe question calculation.
     * Any server instance can independently calculate which question is active at any given time.
     *
     * @param gameStartedAt When the game started (required for elapsed time calculation)
     * @param questions List of questions with their timings, will be sorted by orderIndex
     * @param currentTime The time at which to calculate the active question
     * @return ActiveQuestionResult containing the active question or indicating game has ended
     */
    fun calculate(
        gameStartedAt: Instant,
        questions: List<QuestionTiming>,
        currentTime: Instant
    ): ActiveQuestionResult {
        if (questions.isEmpty()) {
            return ActiveQuestionResult(
                activeQuestion = null,
                expiresAt = null,
                isGameEnded = true
            )
        }

        val elapsedSeconds = Duration.between(gameStartedAt, currentTime).seconds

        // If current time is before game start, return first question
        if (elapsedSeconds < 0) {
            val firstQuestion = questions.minByOrNull { it.orderIndex }!!
            return ActiveQuestionResult(
                activeQuestion = firstQuestion,
                expiresAt = gameStartedAt.plusSeconds(firstQuestion.durationSeconds.toLong()),
                isGameEnded = false,
                questionStartedAt = gameStartedAt
            )
        }

        // Sort questions by order index
        val sortedQuestions = questions.sortedBy { it.orderIndex }

        var cumulativeSeconds = 0L
        for (question in sortedQuestions) {
            val questionStartSeconds = cumulativeSeconds
            cumulativeSeconds += question.durationSeconds

            if (elapsedSeconds < cumulativeSeconds) {
                return ActiveQuestionResult(
                    activeQuestion = question,
                    expiresAt = gameStartedAt.plusSeconds(cumulativeSeconds),
                    isGameEnded = false,
                    questionStartedAt = gameStartedAt.plusSeconds(questionStartSeconds)
                )
            }
        }

        // All questions have ended
        return ActiveQuestionResult(
            activeQuestion = null,
            expiresAt = null,
            isGameEnded = true
        )
    }

    /**
     * Validates if a specific question is still active at the given time.
     * Used to validate answer submissions.
     *
     * @param gameStartedAt When the game started
     * @param questions All questions for the game
     * @param questionId The question ID to check
     * @param submissionTime The time of the submission
     * @return true if the question was active at the submission time
     */
    fun isQuestionStillActive(
        gameStartedAt: Instant,
        questions: List<QuestionTiming>,
        questionId: UUID,
        submissionTime: Instant
    ): Boolean {
        val result = calculate(gameStartedAt, questions, submissionTime)
        return result.activeQuestion?.questionId == questionId
    }

    /**
     * Gets the question that was active at a specific historical time.
     * Useful for auditing and replaying.
     *
     * @param gameStartedAt When the game started
     * @param questions All questions for the game
     * @param targetTime The historical time to check
     * @return The question that was active at that time, or null if none
     */
    fun getQuestionAtTime(
        gameStartedAt: Instant,
        questions: List<QuestionTiming>,
        targetTime: Instant
    ): QuestionTiming? {
        return calculate(gameStartedAt, questions, targetTime).activeQuestion
    }

    /**
     * Calculates the time window for a specific question.
     *
     * @param gameStartedAt When the game started
     * @param questions All questions for the game
     * @param questionId The question to get timing for
     * @return Pair of (startTime, endTime) or null if question not found
     */
    fun getQuestionTimeWindow(
        gameStartedAt: Instant,
        questions: List<QuestionTiming>,
        questionId: UUID
    ): Pair<Instant, Instant>? {
        val sortedQuestions = questions.sortedBy { it.orderIndex }

        var cumulativeSeconds = 0L
        for (question in sortedQuestions) {
            val startTime = gameStartedAt.plusSeconds(cumulativeSeconds)
            cumulativeSeconds += question.durationSeconds
            val endTime = gameStartedAt.plusSeconds(cumulativeSeconds)

            if (question.questionId == questionId) {
                return Pair(startTime, endTime)
            }
        }

        return null
    }

    /**
     * Calculates the total game duration based on all question durations.
     *
     * @param questions All questions for the game
     * @return Total duration in seconds
     */
    fun getTotalGameDuration(questions: List<QuestionTiming>): Long {
        return questions.sumOf { it.durationSeconds.toLong() }
    }
}

package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.UserQuestionAnswer
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Repository
interface UserQuestionAnswerRepository : CassandraRepository<UserQuestionAnswer, UUID> {

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0")
    fun findByUserId(userId: UUID): List<UserQuestionAnswer>

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0 AND game_id = ?1")
    fun findByUserIdAndGameId(userId: UUID, gameId: UUID): List<UserQuestionAnswer>

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0 AND game_id = ?1 AND question_id = ?2")
    fun findByUserIdAndGameIdAndQuestionId(userId: UUID, gameId: UUID, questionId: UUID): UserQuestionAnswer?

    /**
     * Atomically inserts a user answer if it doesn't already exist.
     * Uses Cassandra Lightweight Transactions (LWT) with IF NOT EXISTS.
     *
     * This prevents the race condition where two concurrent requests could both
     * pass the duplicate check and insert answers.
     *
     * @return true if inserted (no duplicate), false if already exists (duplicate)
     */
    @Query("""
        INSERT INTO user_question_answers
        (user_id, game_id, question_id, turn_id, selected_option_id, is_correct, reward_amount, answered_at)
        VALUES (?0, ?1, ?2, ?3, ?4, ?5, ?6, ?7)
        IF NOT EXISTS
    """)
    fun insertIfNotExists(
        userId: UUID,
        gameId: UUID,
        questionId: UUID,
        turnId: UUID,
        selectedOptionId: UUID,
        isCorrect: Boolean,
        rewardAmount: BigDecimal,
        answeredAt: Instant
    ): Boolean
}
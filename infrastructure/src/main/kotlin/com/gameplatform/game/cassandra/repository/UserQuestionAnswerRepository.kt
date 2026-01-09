package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.UserQuestionAnswer
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserQuestionAnswerRepository : CassandraRepository<UserQuestionAnswer, UUID> {

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0")
    fun findByUserId(userId: UUID): List<UserQuestionAnswer>

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0 AND game_id = ?1")
    fun findByUserIdAndGameId(userId: UUID, gameId: UUID): List<UserQuestionAnswer>

    @Query("SELECT * FROM user_question_answers WHERE user_id = ?0 AND game_id = ?1 AND question_id = ?2")
    fun findByUserIdAndGameIdAndQuestionId(userId: UUID, gameId: UUID, questionId: UUID): UserQuestionAnswer?
}
package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.Turn
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TurnRepository : CassandraRepository<Turn, UUID> {

    @Query("SELECT * FROM turns WHERE game_id = ?0 AND question_id = ?1")
    fun findByGameIdAndQuestionId(gameId: UUID, questionId: UUID): List<Turn>

    @Query("SELECT * FROM turns WHERE game_id = ?0 AND question_id = ?1 ORDER BY client_timestamp ASC, server_sequence ASC")
    fun findByGameIdAndQuestionIdOrderByTimestamp(gameId: UUID, questionId: UUID): List<Turn>

    @Query("SELECT * FROM turns WHERE game_id = ?0 AND question_id = ?1 AND client_timestamp >= ?2 AND client_timestamp <= ?3")
    fun findByGameIdAndQuestionIdAndTimestampRange(
        gameId: UUID,
        questionId: UUID,
        startTime: Instant,
        endTime: Instant
    ): List<Turn>
}
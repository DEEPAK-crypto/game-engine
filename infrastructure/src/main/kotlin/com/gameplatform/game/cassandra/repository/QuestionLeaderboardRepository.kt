package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.QuestionLeaderboard
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface QuestionLeaderboardRepository : CassandraRepository<QuestionLeaderboard, UUID> {

    @Query("SELECT * FROM question_leaderboards WHERE game_id = ?0 AND question_id = ?1 ORDER BY rank ASC")
    fun findByGameIdAndQuestionIdOrderByRank(gameId: UUID, questionId: UUID): List<QuestionLeaderboard>

    @Query("SELECT * FROM question_leaderboards WHERE game_id = ?0 AND question_id = ?1 AND rank <= ?2 ORDER BY rank ASC")
    fun findTopNByGameIdAndQuestionId(gameId: UUID, questionId: UUID, topN: Int): List<QuestionLeaderboard>
}
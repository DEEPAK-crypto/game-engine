package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.GameLeaderboard
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GameLeaderboardRepository : CassandraRepository<GameLeaderboard, UUID> {

    @Query("SELECT * FROM game_leaderboards WHERE game_id = ?0 ORDER BY rank ASC")
    fun findByGameIdOrderByRank(gameId: UUID): List<GameLeaderboard>

    @Query("SELECT * FROM game_leaderboards WHERE game_id = ?0 AND rank <= ?1 ORDER BY rank ASC")
    fun findTopNByGameId(gameId: UUID, topN: Int): List<GameLeaderboard>
}
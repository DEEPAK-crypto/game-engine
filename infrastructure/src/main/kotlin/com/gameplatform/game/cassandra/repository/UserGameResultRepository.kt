package com.gameplatform.game.cassandra.repository

import com.gameplatform.game.cassandra.entity.UserGameResult
import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserGameResultRepository : CassandraRepository<UserGameResult, UUID> {

    @Query("SELECT * FROM user_game_results WHERE user_id = ?0")
    fun findByUserId(userId: UUID): List<UserGameResult>

    @Query("SELECT * FROM user_game_results WHERE user_id = ?0 AND game_id = ?1")
    fun findByUserIdAndGameId(userId: UUID, gameId: UUID): UserGameResult?
}
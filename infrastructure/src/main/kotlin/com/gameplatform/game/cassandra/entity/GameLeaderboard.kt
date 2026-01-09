package com.gameplatform.game.cassandra.entity

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.util.UUID

@Table("game_leaderboards")
data class GameLeaderboard(
    @PrimaryKeyColumn(name = "game_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val gameId: UUID,

    @PrimaryKeyColumn(name = "rank", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    val rank: Int,

    @Column("user_id")
    val userId: UUID,

    @Column("total_reward")
    val totalReward: BigDecimal,

    @Column("correct_answers")
    val correctAnswers: Int
)

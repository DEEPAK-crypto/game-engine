package com.gameplatform.game.cassandra.entity

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("question_leaderboards")
data class QuestionLeaderboard(
    @PrimaryKeyColumn(name = "game_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val gameId: UUID,

    @PrimaryKeyColumn(name = "question_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    val questionId: UUID,

    @PrimaryKeyColumn(name = "rank", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    val rank: Int,

    @Column("user_id")
    val userId: UUID,

    @Column("reward_amount")
    val rewardAmount: BigDecimal,

    @Column("answered_at")
    val answeredAt: Instant
)

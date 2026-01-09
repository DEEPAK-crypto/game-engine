package com.gameplatform.game.cassandra.entity

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.util.UUID

@Table("user_game_results")
data class UserGameResult(
    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val userId: UUID,

    @PrimaryKeyColumn(name = "game_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    val gameId: UUID,

    @field:Column("total_reward")
    val totalReward: BigDecimal,

    @field:Column("correct_answers")
    val correctAnswers: Int,

    @field:Column("total_questions")
    val totalQuestions: Int,

    @field:Column("final_rank")
    val finalRank: Int
)

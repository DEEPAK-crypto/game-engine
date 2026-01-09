package com.gameplatform.game.cassandra.entity

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("user_question_answers")
data class UserQuestionAnswer(
    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val userId: UUID,

    @PrimaryKeyColumn(name = "game_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    val gameId: UUID,

    @PrimaryKeyColumn(name = "question_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    val questionId: UUID,

    @field:Column("turn_id")
    val turnId: UUID,

    @field:Column("selected_option_id")
    val selectedOptionId: UUID,

    @field:Column("is_correct")
    val isCorrect: Boolean,

    @field:Column("reward_amount")
    val rewardAmount: BigDecimal,

    @field:Column("answered_at")
    val answeredAt: Instant
)

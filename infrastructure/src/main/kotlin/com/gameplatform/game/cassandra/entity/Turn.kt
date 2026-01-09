package com.gameplatform.game.cassandra.entity

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("turns")
data class Turn(
    @PrimaryKeyColumn(name = "game_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    val gameId: UUID,

    @PrimaryKeyColumn(name = "question_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    val questionId: UUID,

    @PrimaryKeyColumn(name = "client_timestamp", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    val clientTimestamp: Instant,

    @PrimaryKeyColumn(name = "server_sequence", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    val serverSequence: Long,

    @field:Column("turn_id") // Add this to be explicit
    val turnId: UUID,

    @field:Column("user_id")
    val userId: UUID,

    @field:Column("selected_option_id")
    val selectedOptionId: UUID,

    @field:Column("is_correct")
    val isCorrect: Boolean,

    @field:Column("reward_amount")
    val rewardAmount: BigDecimal,

    @field:Column("server_timestamp")
    val serverTimestamp: Instant
)

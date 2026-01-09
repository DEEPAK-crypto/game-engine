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

    @Column("turn_id")
    val turnId: UUID,

    @Column("user_id")
    val userId: UUID,

    @Column("selected_option_id")
    val selectedOptionId: UUID,

    @Column("is_correct")
    val isCorrect: Boolean,

    @Column("reward_amount")
    val rewardAmount: BigDecimal,

    @Column("server_timestamp")
    val serverTimestamp: Instant
)

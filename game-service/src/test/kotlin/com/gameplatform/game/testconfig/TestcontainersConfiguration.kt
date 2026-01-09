package com.gameplatform.game.testconfig

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    companion object {
        private val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgres:15-alpine")
        )
            .withDatabaseName("game_platform_test")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }

        private val cassandraContainer: CassandraContainer<*> = CassandraContainer(
            DockerImageName.parse("cassandra:4.1")
        )
            .apply {
                start()
                // Create keyspace
                execInContainer(
                    "cqlsh",
                    "-e",
                    "CREATE KEYSPACE IF NOT EXISTS game_platform WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
                )
                // Create tables
                val schema = """
                    CREATE TABLE IF NOT EXISTS game_platform.turns (
                        game_id uuid,
                        question_id uuid,
                        client_timestamp timestamp,
                        server_sequence bigint,
                        turn_id uuid,
                        user_id uuid,
                        selected_option_id uuid,
                        is_correct boolean,
                        reward_amount decimal,
                        server_timestamp timestamp,
                        PRIMARY KEY ((game_id, question_id), client_timestamp, server_sequence)
                    ) WITH CLUSTERING ORDER BY (client_timestamp ASC, server_sequence ASC);

                    CREATE TABLE IF NOT EXISTS game_platform.user_question_answers (
                        user_id uuid,
                        game_id uuid,
                        question_id uuid,
                        turn_id uuid,
                        selected_option_id uuid,
                        is_correct boolean,
                        reward_amount decimal,
                        answered_at timestamp,
                        PRIMARY KEY (user_id, game_id, question_id)
                    );

                    CREATE TABLE IF NOT EXISTS game_platform.user_game_results (
                        user_id uuid,
                        game_id uuid,
                        total_reward decimal,
                        correct_answers int,
                        total_questions int,
                        final_rank int,
                        PRIMARY KEY (user_id, game_id)
                    );
                """.trimIndent()

                execInContainer("cqlsh", "-e", schema)
            }

        private val redisContainer: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("redis:7-alpine")
        )
            .withExposedPorts(6379)
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)

            registry.add("spring.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.cassandra.port") { cassandraContainer.firstMappedPort }
            registry.add("spring.cassandra.local-datacenter") { "datacenter1" }
            registry.add("spring.cassandra.keyspace-name") { "game_platform" }

            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.firstMappedPort }
        }
    }

    @Bean
    fun postgresContainer(): PostgreSQLContainer<*> = postgresContainer

    @Bean
    fun cassandraContainer(): CassandraContainer<*> = cassandraContainer

    @Bean
    fun redisContainer(): GenericContainer<*> = redisContainer
}
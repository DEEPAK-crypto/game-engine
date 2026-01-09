package com.gameplatform.game.testconfig

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
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
                // Create keyspace manually as init scripts can be unreliable
                execInContainer(
                    "cqlsh",
                    "-e",
                    "CREATE KEYSPACE IF NOT EXISTS game_platform WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
                )
            }

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
        }
    }

    @Bean
    fun postgresContainer(): PostgreSQLContainer<*> = postgresContainer

    @Bean
    fun cassandraContainer(): CassandraContainer<*> = cassandraContainer
}
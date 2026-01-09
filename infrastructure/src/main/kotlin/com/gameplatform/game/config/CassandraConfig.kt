package com.gameplatform.game.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories

@Configuration
@EnableCassandraRepositories(basePackages = ["com.gameplatform.game.cassandra.repository"])
class CassandraConfig : AbstractCassandraConfiguration() {

    @Value("\${spring.cassandra.keyspace-name}")
    private lateinit var keyspaceName: String

    @Value("\${spring.cassandra.contact-points}")
    private lateinit var contactPoints: String

    @Value("\${spring.cassandra.port}")
    private var port: Int = 9042

    @Value("\${spring.cassandra.local-datacenter}")
    private lateinit var localDatacenter: String

    override fun getKeyspaceName(): String = keyspaceName

    override fun getContactPoints(): String = contactPoints

    override fun getPort(): Int = port

    override fun getLocalDataCenter(): String = localDatacenter

    override fun getSchemaAction(): SchemaAction = SchemaAction.NONE
}
package com.gameplatform.game.config

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

@Configuration
class PostgresConfig {

    @Bean
    fun dslContext(dataSource: DataSource): DSLContext {
        val configuration = DefaultConfiguration()
        configuration.set(TransactionAwareDataSourceProxy(dataSource))
        configuration.set(SQLDialect.POSTGRES)
        return DSL.using(configuration)
    }
}

package com.gameplatform.scheduler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import javax.sql.DataSource

@Configuration
class QuartzConfig {

    @Bean
    fun schedulerFactoryBean(dataSource: DataSource): SchedulerFactoryBean {
        val factory = SchedulerFactoryBean()
        factory.setDataSource(dataSource)
        factory.setOverwriteExistingJobs(true)
        factory.setWaitForJobsToCompleteOnShutdown(true)
        factory.setAutoStartup(true)
        return factory
    }
}
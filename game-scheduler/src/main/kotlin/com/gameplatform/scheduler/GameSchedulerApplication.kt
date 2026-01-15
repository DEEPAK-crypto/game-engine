package com.gameplatform.scheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class GameSchedulerApplication

fun main(args: Array<String>) {
    runApplication<GameSchedulerApplication>(*args)
}
package com.gameplatform.game

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(scanBasePackages = ["com.gameplatform.game"])
class GameServiceApplication {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Game Platform API")
                    .description("Distributed trivia game platform with real-time question management and leaderboards")
                    .version("1.0.0")
                    .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html"))
            )
    }
}

fun main(args: Array<String>) {
    runApplication<GameServiceApplication>(*args)
}

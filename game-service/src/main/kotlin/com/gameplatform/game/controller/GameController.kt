package com.gameplatform.game.controller

import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.dto.GameResponse
import com.gameplatform.game.dto.StartGameRequest
import com.gameplatform.game.service.GameService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/games")
class GameController(
    private val gameService: GameService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGame(@Valid @RequestBody request: CreateGameRequest): GameResponse {
        return gameService.createGame(request)
    }

    @GetMapping("/{gameId}")
    fun getGame(@PathVariable gameId: UUID): GameResponse {
        return gameService.getGame(gameId)
    }

    @GetMapping
    fun getAllGames(
        @RequestParam(required = false) status: GameStatus?
    ): List<GameResponse> {
        return if (status != null) {
            gameService.getGamesByStatus(status)
        } else {
            gameService.getAllGames()
        }
    }

    @PostMapping("/{gameId}/start")
    fun startGame(
        @PathVariable gameId: UUID,
        @Valid @RequestBody request: StartGameRequest = StartGameRequest()
    ): GameResponse {
        return gameService.startGame(gameId, request.startAt)
    }

    @PostMapping("/{gameId}/complete")
    fun completeGame(@PathVariable gameId: UUID): GameResponse {
        return gameService.completeGame(gameId)
    }

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGame(@PathVariable gameId: UUID) {
        gameService.deleteGame(gameId)
    }
}
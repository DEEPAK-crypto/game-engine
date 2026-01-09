package com.gameplatform.game.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.gameplatform.game.domain.enums.GameStatus
import com.gameplatform.game.domain.enums.GameType
import com.gameplatform.game.dto.CreateGameRequest
import com.gameplatform.game.testconfig.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class GameControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `should create game via REST API`() {
        // Given
        val request = CreateGameRequest(
            name = "API Test Game",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("1500.00"),
            questionTimerSeconds = 45
        )

        // When/Then
        mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value(request.name))
            .andExpect(jsonPath("$.initialBudget").value(request.initialBudget.toDouble()))
            .andExpect(jsonPath("$.status").value(GameStatus.DRAFT.name))
    }

    @Test
    fun `should validate game creation request`() {
        // Given - invalid request with empty name
        val invalidRequest = CreateGameRequest(
            name = "",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("1000.00"),
            questionTimerSeconds = 30
        )

        // When/Then
        mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.validationErrors").isArray)
    }

    @Test
    fun `should get game by id`() {
        // Given - create a game first
        val createRequest = CreateGameRequest(
            name = "Get Test Game",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("1000.00"),
            questionTimerSeconds = 30
        )

        val createResult = mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val gameId = objectMapper.readTree(createResult.response.contentAsString)
            .get("id").asText()

        // When/Then
        mockMvc.perform(get("/api/games/$gameId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(gameId))
            .andExpect(jsonPath("$.name").value(createRequest.name))
    }

    @Test
    fun `should start game via REST API`() {
        // Given - create a game first
        val createRequest = CreateGameRequest(
            name = "Start Test Game",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("1000.00"),
            questionTimerSeconds = 30
        )

        val createResult = mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val gameId = objectMapper.readTree(createResult.response.contentAsString)
            .get("id").asText()

        // When/Then
        mockMvc.perform(
            post("/api/games/$gameId/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(gameId))
            .andExpect(jsonPath("$.status").value(GameStatus.ACTIVE.name))
            .andExpect(jsonPath("$.startedAt").exists())
    }

    @Test
    fun `should return 404 when game not found`() {
        // Given
        val nonExistentId = java.util.UUID.randomUUID()

        // When/Then
        mockMvc.perform(get("/api/games/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should list all games`() {
        // Given - create multiple games
        val request1 = CreateGameRequest(
            name = "Game 1",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("1000.00"),
            questionTimerSeconds = 30
        )

        val request2 = CreateGameRequest(
            name = "Game 2",
            gameType = GameType.MCQ_FIFO,
            initialBudget = BigDecimal("2000.00"),
            questionTimerSeconds = 30
        )

        mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
        )

        mockMvc.perform(
            post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
        )

        // When/Then
        mockMvc.perform(get("/api/games"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
    }
}
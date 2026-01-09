package com.gameplatform.game.gatling

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Gatling Load Test Simulation for Game Platform (Kotlin)
 *
 * Simulates 10,000 concurrent users playing a game with 10-second question expiry.
 *
 * Run with: ./gradlew gatlingRun
 */
class GameLoadSimulation : Simulation() {

    // Configuration
    private val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
    private val numQuestions = System.getProperty("numQuestions", "5").toInt()
    private val questionTimer = System.getProperty("questionTimer", "10").toInt()
    private val questionReward = System.getProperty("questionReward", "100.00").toDouble()
    private val targetUsers = System.getProperty("targetUsers", "10000").toInt()

    // Shared state for gameId
    @Volatile
    private var sharedGameId: String = ""

    // HTTP Protocol Configuration
    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Load Test - Kotlin")

    // Feeder for generating user IDs
    private val userIdFeeder = generateSequence {
        mapOf("userId" to UUID.randomUUID().toString())
    }.iterator()

    // Scenario: Setup Game
    private val setupScenario = scenario("Setup Game")
        .exec(
            http("Create Game")
                .post("/api/games")
                .body(StringBody("""
                    {
                        "name": "Gatling Load Test - $targetUsers Users",
                        "gameType": "MCQ_FIFO",
                        "initialBudget": ${numQuestions * questionReward * 0.5},
                        "questionTimerSeconds": $questionTimer
                    }
                """.trimIndent()))
                .asJson()
                .check(status().shouldBe(201))
                .check(jsonPath("$.id").saveAs("gameId"))
        )
        .pause(1)
        .exec { session ->
            val gameId = session.getString("gameId")
            sharedGameId = gameId!!
            println("✓ Game created: $gameId")
            session
        }
        .exec(
            http("Add Questions")
                .post("/api/games/#{gameId}/questions")
                .body(StringBody { session ->
                    val questions = (1..numQuestions).joinToString(",\n", "[", "]") { i ->
                        """
                        {
                            "questionText": "Load Test Question $i - What is the answer?",
                            "options": [
                                {"optionText": "Correct Answer"},
                                {"optionText": "Wrong Answer 1"},
                                {"optionText": "Wrong Answer 2"},
                                {"optionText": "Wrong Answer 3"}
                            ],
                            "correctOptionIndex": 0,
                            "reward": $questionReward,
                            "durationSeconds": $questionTimer
                        }
                        """.trimIndent()
                    }
                    questions
                })
                .asJson()
                .check(status().shouldBe(201))
        )
        .pause(1)
        .exec { session ->
            println("✓ Added $numQuestions questions")
            session
        }
        .exec(
            http("Start Game")
                .post("/api/games/#{gameId}/start")
                .body(StringBody("{}"))
                .asJson()
                .check(status().shouldBe(200))
        )
        .exec { session ->
            println("✓ Game started")
            println("\n========================================")
            println("Starting load test with $targetUsers users")
            println("========================================\n")
            session
        }

    // Scenario: User Playing Game
    private val playGameScenario = scenario("User Playing Game")
        .exec { session -> session.set("gameId", sharedGameId) }
        .feed(userIdFeeder)
        .repeat(numQuestions, "questionNum").on(
            // Wait for previous question to expire (except first question)
            doIf { session ->
                val qNum = session.getInt("questionNum")
                qNum > 0
            }.then(
                pause(Duration.ofSeconds((questionTimer + 2).toLong()))
            )
            // Get active question
            .exec(
                http("Get Active Question")
                    .get("/api/games/#{gameId}/questions/active")
                    .check(status().`in`(200, 404))
                    .check(bodyString().saveAs("activeQuestionBody"))
            )
            // Extract question details if we got a 200 response
            .exec { session ->
                val body = session.getString("activeQuestionBody")
                if (body != null && body.contains("\"question\"")) {
                    // Parse the JSON manually using regex
                    val questionIdMatch = """"question"[^}]*"id"\s*:\s*"([^"]+)"""".toRegex().find(body)
                    val optionIdMatch = """"options"[^\]]*"id"\s*:\s*"([^"]+)"""".toRegex().find(body)
                    val remainingMatch = """"remainingSeconds"\s*:\s*(\d+)""".toRegex().find(body)

                    var newSession = session
                    questionIdMatch?.groupValues?.getOrNull(1)?.let {
                        newSession = newSession.set("currentQuestionId", it)
                    }
                    optionIdMatch?.groupValues?.getOrNull(1)?.let {
                        newSession = newSession.set("correctOptionId", it)
                    }
                    remainingMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        newSession = newSession.set("remainingSeconds", it)
                    }
                    newSession
                } else {
                    session
                }
            }
            // Submit answer if question is active
            .doIf { session -> session.contains("currentQuestionId") }.then(
                // Think time before answering
                exec { session ->
                    val remaining = session.getInt("remainingSeconds")
                    if (remaining >= 2) {
                        val maxThinkTime = minOf(3, remaining - 1)
                        val thinkTime = (Math.random() * maxThinkTime).toLong()
                        Thread.sleep(thinkTime * 1000)
                    }
                    session
                }
                .exec(
                    http("Submit Answer")
                        .post("/api/games/#{gameId}/questions/submit")
                        .body(StringBody { session ->
                            // 90% correct, 10% wrong answers
                            val isCorrect = Math.random() < 0.9
                            val optionId = if (isCorrect) {
                                session.getString("correctOptionId")
                            } else {
                                UUID.randomUUID().toString()
                            }

                            """
                            {
                                "userId": "${session.getString("userId")}",
                                "selectedOptionId": "$optionId",
                                "clientTimestamp": "${Instant.now()}"
                            }
                            """.trimIndent()
                        })
                        .asJson()
                        .check(status().`in`(200, 400)) // 400 is OK (duplicate)
                        .checkIf { response, _ -> response.status().code() == 200 }.then(
                            jsonPath("$.isCorrect").ofBoolean().saveAs("isCorrect"),
                            jsonPath("$.rewardAmount").ofDouble().saveAs("rewardAmount"),
                            jsonPath("$.rank").ofInt().optional().saveAs("rank")
                        )
                )
                .exec { session ->
                    if (session.contains("isCorrect")) {
                        val userId = session.getString("userId")
                        val correct = session.getBoolean("isCorrect")
                        val reward = session.getDouble("rewardAmount")
                        val rank = session.get<Int>("rank")

                        // Log first few submissions (sample)
                        if (userId.hashCode() % 1000 == 0) {
                            println("User ${userId?.take(8)}: Correct=$correct, Reward=$$${String.format("%.2f", reward)}, Rank=${rank ?: "N/A"}")
                        }
                    }
                    session
                }
            )
            .pause(Duration.ofMillis(100))
        )

    // Scenario: Teardown
    private val teardownScenario = scenario("Teardown")
        .exec { session -> session.set("gameId", sharedGameId) }
        .exec { session ->
            println("\n========================================")
            println("Load test completed, tearing down...")
            println("========================================\n")
            session
        }
        .exec(
            http("Get Final Leaderboard")
                .get("/api/leaderboards/games/#{gameId}?limit=20")
                .check(status().shouldBe(200))
                .check(bodyString().saveAs("leaderboard"))
        )
        .exec { session ->
            println("Top 20 Leaderboard:")
            println(session.getString("leaderboard"))
            session
        }
        .exec(
            http("Complete Game")
                .post("/api/games/#{gameId}/complete")
                .check(status().shouldBe(200))
                .check(jsonPath("$.remainingBudget").ofDouble().saveAs("remainingBudget"))
        )
        .exec { session ->
            println("\n✓ Game completed")
            println("  Remaining budget: $${session.getDouble("remainingBudget")}")
            println("\n========================================")
            println("Gatling Load Test Complete!")
            println("========================================\n")
            session
        }

    // Test Execution Plan
    init {
        setUp(
            // First: Setup the game (1 user)
            setupScenario.injectOpen(atOnceUsers(1))
                .andThen(
                    // Then: Ramp up users playing the game
                    playGameScenario.injectOpen(
                        rampUsers(1000).during(Duration.ofSeconds(30)),  // 0 → 1k users over 30s
                        rampUsers(4000).during(Duration.ofSeconds(30)),  // 1k → 5k users over 30s
                        rampUsers(5000).during(Duration.ofSeconds(30)),  // 5k → 10k users over 30s
                        constantUsersPerSec(100.0).during(
                            Duration.ofSeconds((numQuestions * (questionTimer + 2)).toLong())
                        )
                    )
                )
                .andThen(
                    // Finally: Teardown (1 user)
                    teardownScenario.injectOpen(atOnceUsers(1))
                )
        ).protocols(httpProtocol)
         .assertions(
             global().responseTime().percentile3().lt(2000),  // 95th percentile < 2s
             global().responseTime().percentile4().lt(5000),  // 99th percentile < 5s
             global().successfulRequests().percent().gt(80.0) // > 80% success rate
         )
    }
}
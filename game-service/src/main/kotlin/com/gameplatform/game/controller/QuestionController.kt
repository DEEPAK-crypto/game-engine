package com.gameplatform.game.controller

import com.gameplatform.game.dto.*
import com.gameplatform.game.service.AnswerSubmissionService
import com.gameplatform.game.service.QuestionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/games/{gameId}/questions")
class QuestionController(
    private val questionService: QuestionService,
    private val answerSubmissionService: AnswerSubmissionService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addQuestions(
        @PathVariable gameId: UUID,
        @Valid @RequestBody requests: List<CreateQuestionRequest>
    ): List<QuestionResponse> {
        return questionService.addQuestions(gameId, requests)
    }

    @GetMapping
    fun getQuestions(@PathVariable gameId: UUID): List<QuestionResponse> {
        return questionService.getQuestionsByGame(gameId)
    }

    @GetMapping("/active")
    fun getActiveQuestion(@PathVariable gameId: UUID): ActiveQuestionResponse? {
        return questionService.getActiveQuestion(gameId)
    }

    @PostMapping("/submit")
    fun submitAnswer(
        @PathVariable gameId: UUID,
        @Valid @RequestBody request: SubmitAnswerRequest
    ): AnswerSubmissionResponse {
        return answerSubmissionService.submitAnswer(gameId, request)
    }
}

@RestController
@RequestMapping("/api/questions")
class QuestionManagementController(
    private val questionService: QuestionService
) {

    @GetMapping("/{questionId}")
    fun getQuestion(@PathVariable questionId: UUID): QuestionResponse {
        return questionService.getQuestion(questionId)
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteQuestion(@PathVariable questionId: UUID) {
        questionService.deleteQuestion(questionId)
    }
}
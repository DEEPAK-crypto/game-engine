package com.gameplatform.game.exception

import com.gameplatform.game.controller.dto.ErrorResponse
import com.gameplatform.game.controller.dto.FieldError
import com.gameplatform.game.controller.dto.ValidationErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GameNotFoundException::class)
    fun handleGameNotFound(ex: GameNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.NOT_FOUND, request)
    }

    @ExceptionHandler(QuestionNotFoundException::class)
    fun handleQuestionNotFound(ex: QuestionNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.NOT_FOUND, request)
    }

    @ExceptionHandler(QuestionOptionNotFoundException::class)
    fun handleOptionNotFound(ex: QuestionOptionNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.NOT_FOUND, request)
    }

    @ExceptionHandler(
        GameAlreadyStartedException::class,
        GameAlreadyCompletedException::class,
        GameNotStartedException::class,
        InvalidGameStateException::class,
        DuplicateAnswerException::class,
        AnswerSubmissionClosedException::class,
        NoActiveQuestionException::class
    )
    fun handleBadRequest(ex: GamePlatformException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(
        InsufficientBudgetException::class,
        BudgetAllocationException::class
    )
    fun handleBudgetErrors(ex: GamePlatformException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.CONFLICT, request)
    }

    @ExceptionHandler(
        InvalidQuestionOrderException::class,
        InvalidAnswerException::class,
        InvalidOptionException::class,
        ValidationException::class
    )
    fun handleValidationErrors(ex: GamePlatformException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(ConcurrencyException::class)
    fun handleConcurrency(ex: ConcurrencyException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return createErrorResponse(ex, HttpStatus.CONFLICT, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ValidationErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map {
            FieldError(field = it.field, message = it.defaultMessage ?: "Invalid value")
        }

        val response = ValidationErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed",
            path = getPath(request),
            validationErrors = errors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException, request: WebRequest): ResponseEntity<ValidationErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map {
            FieldError(field = it.field, message = it.defaultMessage ?: "Invalid value")
        }

        val response = ValidationErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed",
            path = getPath(request),
            validationErrors = errors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)
        return createErrorResponse(
            message = "An unexpected error occurred",
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            request = request
        )
    }

    private fun createErrorResponse(
        ex: Exception,
        status: HttpStatus,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message ?: "An error occurred",
            path = getPath(request)
        )
        return ResponseEntity.status(status).body(response)
    }

    private fun createErrorResponse(
        message: String,
        status: HttpStatus,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = getPath(request)
        )
        return ResponseEntity.status(status).body(response)
    }

    private fun getPath(request: WebRequest): String? {
        return request.getDescription(false).removePrefix("uri=")
    }
}

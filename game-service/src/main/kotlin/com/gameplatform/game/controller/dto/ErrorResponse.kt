package com.gameplatform.game.controller.dto

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String?
)

data class ValidationErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String?,
    val validationErrors: List<FieldError>
)

data class FieldError(
    val field: String,
    val message: String
)
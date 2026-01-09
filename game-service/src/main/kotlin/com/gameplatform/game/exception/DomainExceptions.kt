package com.gameplatform.game.exception

import java.util.UUID

sealed class GamePlatformException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

// Game exceptions
class GameNotFoundException(gameId: UUID) : GamePlatformException("Game not found: $gameId")

class GameAlreadyStartedException(gameId: UUID) : GamePlatformException("Game already started: $gameId")

class GameNotStartedException(gameId: UUID) : GamePlatformException("Game not started: $gameId")

class GameAlreadyCompletedException(gameId: UUID) : GamePlatformException("Game already completed: $gameId")

class InvalidGameStateException(message: String) : GamePlatformException(message)

// Question exceptions
class QuestionNotFoundException(questionId: UUID) : GamePlatformException("Question not found: $questionId")

class QuestionNotActiveException(questionId: UUID) : GamePlatformException("Question not currently active: $questionId")

class NoActiveQuestionException(gameId: UUID) : GamePlatformException("No active question for game: $gameId")

class InvalidQuestionOrderException(message: String) : GamePlatformException(message)

// Answer exceptions
class DuplicateAnswerException(userId: UUID, questionId: UUID) :
    GamePlatformException("User $userId already answered question $questionId")

class InvalidAnswerException(message: String) : GamePlatformException(message)

class AnswerSubmissionClosedException(questionId: UUID) :
    GamePlatformException("Answer submission closed for question: $questionId")

// Budget exceptions
class InsufficientBudgetException(gameId: UUID, required: String, available: String) :
    GamePlatformException("Insufficient budget for game $gameId. Required: $required, Available: $available")

class BudgetAllocationException(message: String) : GamePlatformException(message)

// Option exceptions
class QuestionOptionNotFoundException(optionId: UUID) : GamePlatformException("Question option not found: $optionId")

class InvalidOptionException(message: String) : GamePlatformException(message)

// Validation exceptions
class ValidationException(message: String) : GamePlatformException(message)

class ConcurrencyException(message: String) : GamePlatformException(message)
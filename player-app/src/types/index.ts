// Game Types
export interface Game {
  id: string;
  title: string;
  description: string;
  status: GameStatus;
  maxPlayers: number;
  currentPlayers: number;
  scheduledStartTime?: string;
  actualStartTime?: string;
  endTime?: string;
  hostId: string;
  createdAt: string;
  updatedAt: string;
}

export type GameStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

// Question Types
export interface Question {
  id: string;
  gameId: string;
  questionText: string;
  options: string[];
  correctOptionIndex?: number; // Hidden from players until question ends
  durationSeconds: number;
  points: number;
  sequenceNumber: number;
  status: QuestionStatus;
}

export type QuestionStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED';

// Player-specific types
export interface AnswerSubmission {
  questionId: string;
  selectedOptionIndex: number;
  submittedAt: string;
}

export interface AnswerResult {
  questionId: string;
  selectedOptionIndex: number;
  correctOptionIndex: number;
  isCorrect: boolean;
  pointsEarned: number;
  responseTimeMs: number;
  rank?: number;
}

export interface PlayerGameStats {
  playerId: string;
  gameId: string;
  totalScore: number;
  correctAnswers: number;
  totalAnswers: number;
  avgResponseTime: number;
  rank: number;
}

// Leaderboard Types
export interface LeaderboardEntry {
  rank: number;
  playerId: string;
  playerName: string;
  score: number;
  correctAnswers: number;
  totalAnswers: number;
  avgResponseTime: number;
}

// Auth Types
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  userId: string;
  username: string;
  roles: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  displayName: string;
}

// WebSocket Event Types
export interface GameEvent {
  eventType: GameEventType;
  gameId: string;
  timestamp: string;
  payload: unknown;
}

export type GameEventType =
  | 'GAME_STARTED'
  | 'GAME_COMPLETED'
  | 'GAME_PAUSED'
  | 'QUESTION_ACTIVATED'
  | 'QUESTION_EXPIRED'
  | 'ANSWER_RECEIVED'
  | 'QUESTION_WINNER'
  | 'LEADERBOARD_UPDATE'
  | 'COUNTDOWN'
  | 'PLAYER_COUNT';

export interface PlayerCountPayload {
  playerCount: number;
}

export interface CountdownPayload {
  secondsRemaining: number;
}

export interface QuestionActivatedPayload {
  questionId: string;
  questionText: string;
  options: string[];
  questionNumber: number;
  totalQuestions: number;
  durationSeconds: number;
  expiresAt: string;
}

export interface QuestionExpiredPayload {
  questionId: string;
  correctOptionIndex: number;
}

export interface AnswerReceivedPayload {
  questionId: string;
  accepted: boolean;
}

export interface QuestionWinnerPayload {
  questionId: string;
  winnerId: string;
  winnerName: string;
  points: number;
}

export interface LeaderboardUpdatePayload {
  entries: LeaderboardEntry[];
}

// API Response Types
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

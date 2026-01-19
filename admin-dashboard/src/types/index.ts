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

export interface Question {
  id: string;
  gameId: string;
  questionText: string;
  options: string[];
  correctOptionIndex: number;
  durationSeconds: number;
  points: number;
  sequenceNumber: number;
  status: QuestionStatus;
}

export type QuestionStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED';

// Schedule Types
export interface ScheduledGame {
  id: string;
  gameId: string;
  scheduledStartTime: string;
  scheduledEndTime?: string;
  status: ScheduleStatus;
  createdAt: string;
  createdBy: string;
}

export type ScheduleStatus = 'PENDING' | 'STARTED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

// User Types
export interface User {
  id: string;
  username: string;
  displayName: string;
  role: UserRole;
  createdAt: string;
  lastLoginAt?: string;
}

export type UserRole = 'PLAYER' | 'HOST' | 'ADMIN';

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

// Metrics Types
export interface SystemMetrics {
  activeGames: number;
  totalPlayers: number;
  gamesCompletedToday: number;
  avgResponseTime: number;
  errorRate: number;
  cpuUsage: number;
  memoryUsage: number;
}

export interface GameMetrics {
  gameId: string;
  playerCount: number;
  questionsAnswered: number;
  averageScore: number;
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

// WebSocket Event Types
export interface GameEvent {
  eventType: string;
  gameId: string;
  timestamp: string;
  payload: unknown;
}

export interface PlayerCountEvent {
  gameId: string;
  playerCount: number;
}

export interface QuestionActivatedEvent {
  gameId: string;
  questionId: string;
  questionNumber: number;
  totalQuestions: number;
  expiresAt: string;
}

// API Response Types
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

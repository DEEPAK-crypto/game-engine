import { create } from 'zustand';
import type {
  Question,
  AnswerResult,
  LeaderboardEntry,
  GameEvent,
  QuestionActivatedPayload,
  QuestionExpiredPayload,
  LeaderboardUpdatePayload,
  PlayerCountPayload,
  CountdownPayload,
  QuestionWinnerPayload,
} from '@/types';

export type GameStatus = 'idle' | 'lobby' | 'playing' | 'question' | 'waiting' | 'completed';

interface GameState {
  // Game state
  status: GameStatus;
  gameId: string | null;
  gameTitle: string | null;

  // Question state
  currentQuestion: Question | null;
  questionNumber: number;
  totalQuestions: number;
  timeRemaining: number;
  expiresAt: string | null;

  // Answer state
  selectedOption: number | null;
  submitted: boolean;
  result: AnswerResult | null;
  isCorrect: boolean | null;
  correctOptionIndex: number | null;

  // Leaderboard
  leaderboard: LeaderboardEntry[];
  playerRank: number | null;
  playerScore: number;

  // Player count
  playerCount: number;

  // Countdown
  countdownSeconds: number | null;

  // Winner
  questionWinner: { name: string; points: number } | null;

  // Actions
  setGame: (gameId: string, title: string) => void;
  setStatus: (status: GameStatus) => void;
  setQuestion: (question: Question, questionNumber: number, totalQuestions: number, expiresAt: string) => void;
  selectOption: (optionIndex: number) => void;
  submitAnswer: () => void;
  setResult: (result: AnswerResult) => void;
  showCorrectAnswer: (correctOptionIndex: number) => void;
  updateLeaderboard: (entries: LeaderboardEntry[]) => void;
  updatePlayerCount: (count: number) => void;
  updateCountdown: (seconds: number) => void;
  setQuestionWinner: (name: string, points: number) => void;
  updateTimeRemaining: (seconds: number) => void;
  resetQuestion: () => void;
  resetGame: () => void;
  handleGameEvent: (event: GameEvent) => void;
}

export const useGame = create<GameState>((set, get) => ({
  // Initial state
  status: 'idle',
  gameId: null,
  gameTitle: null,
  currentQuestion: null,
  questionNumber: 0,
  totalQuestions: 0,
  timeRemaining: 0,
  expiresAt: null,
  selectedOption: null,
  submitted: false,
  result: null,
  isCorrect: null,
  correctOptionIndex: null,
  leaderboard: [],
  playerRank: null,
  playerScore: 0,
  playerCount: 0,
  countdownSeconds: null,
  questionWinner: null,

  // Actions
  setGame: (gameId: string, title: string) => {
    set({
      gameId,
      gameTitle: title,
      status: 'lobby',
    });
  },

  setStatus: (status: GameStatus) => {
    set({ status });
  },

  setQuestion: (question: Question, questionNumber: number, totalQuestions: number, expiresAt: string) => {
    const now = Date.now();
    const expiresAtTime = new Date(expiresAt).getTime();
    const timeRemaining = Math.max(0, Math.floor((expiresAtTime - now) / 1000));

    set({
      status: 'question',
      currentQuestion: question,
      questionNumber,
      totalQuestions,
      timeRemaining,
      expiresAt,
      selectedOption: null,
      submitted: false,
      result: null,
      isCorrect: null,
      correctOptionIndex: null,
      questionWinner: null,
    });
  },

  selectOption: (optionIndex: number) => {
    const { submitted } = get();
    if (!submitted) {
      set({ selectedOption: optionIndex });
    }
  },

  submitAnswer: () => {
    set({ submitted: true, status: 'waiting' });
  },

  setResult: (result: AnswerResult) => {
    set({
      result,
      isCorrect: result.isCorrect,
      correctOptionIndex: result.correctOptionIndex,
      playerScore: get().playerScore + result.pointsEarned,
      playerRank: result.rank ?? get().playerRank,
    });
  },

  showCorrectAnswer: (correctOptionIndex: number) => {
    set({
      correctOptionIndex,
      status: 'waiting',
    });
  },

  updateLeaderboard: (entries: LeaderboardEntry[]) => {
    set({ leaderboard: entries });
  },

  updatePlayerCount: (count: number) => {
    set({ playerCount: count });
  },

  updateCountdown: (seconds: number) => {
    set({ countdownSeconds: seconds });
  },

  setQuestionWinner: (name: string, points: number) => {
    set({ questionWinner: { name, points } });
  },

  updateTimeRemaining: (seconds: number) => {
    set({ timeRemaining: seconds });
  },

  resetQuestion: () => {
    set({
      currentQuestion: null,
      selectedOption: null,
      submitted: false,
      result: null,
      isCorrect: null,
      correctOptionIndex: null,
      timeRemaining: 0,
      expiresAt: null,
      questionWinner: null,
    });
  },

  resetGame: () => {
    set({
      status: 'idle',
      gameId: null,
      gameTitle: null,
      currentQuestion: null,
      questionNumber: 0,
      totalQuestions: 0,
      timeRemaining: 0,
      expiresAt: null,
      selectedOption: null,
      submitted: false,
      result: null,
      isCorrect: null,
      correctOptionIndex: null,
      leaderboard: [],
      playerRank: null,
      playerScore: 0,
      playerCount: 0,
      countdownSeconds: null,
      questionWinner: null,
    });
  },

  handleGameEvent: (event: GameEvent) => {
    const state = get();

    switch (event.eventType) {
      case 'GAME_STARTED':
        set({ status: 'playing', countdownSeconds: null });
        break;

      case 'GAME_COMPLETED':
        set({ status: 'completed' });
        break;

      case 'QUESTION_ACTIVATED': {
        const payload = event.payload as QuestionActivatedPayload;
        const question: Question = {
          id: payload.questionId,
          gameId: state.gameId || '',
          questionText: payload.questionText,
          options: payload.options,
          durationSeconds: payload.durationSeconds,
          points: 0,
          sequenceNumber: payload.questionNumber,
          status: 'ACTIVE',
        };
        state.setQuestion(question, payload.questionNumber, payload.totalQuestions, payload.expiresAt);
        break;
      }

      case 'QUESTION_EXPIRED': {
        const payload = event.payload as QuestionExpiredPayload;
        state.showCorrectAnswer(payload.correctOptionIndex);
        break;
      }

      case 'QUESTION_WINNER': {
        const payload = event.payload as QuestionWinnerPayload;
        state.setQuestionWinner(payload.winnerName, payload.points);
        break;
      }

      case 'LEADERBOARD_UPDATE': {
        const payload = event.payload as LeaderboardUpdatePayload;
        state.updateLeaderboard(payload.entries);
        break;
      }

      case 'PLAYER_COUNT': {
        const payload = event.payload as PlayerCountPayload;
        state.updatePlayerCount(payload.playerCount);
        break;
      }

      case 'COUNTDOWN': {
        const payload = event.payload as CountdownPayload;
        state.updateCountdown(payload.secondsRemaining);
        break;
      }

      default:
        console.log('[Game] Unhandled event:', event.eventType);
    }
  },
}));

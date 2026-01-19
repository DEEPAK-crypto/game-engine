import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import type {
  Game,
  Question,
  LeaderboardEntry,
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  PagedResponse,
  AnswerResult,
  PlayerGameStats,
} from '@/types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

class ApiService {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.client.interceptors.request.use(this.addAuthHeader);
    this.client.interceptors.response.use(
      (response) => response,
      this.handleError
    );
  }

  private addAuthHeader = (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  };

  private handleError = async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        try {
          const response = await this.refreshToken(refreshToken);
          localStorage.setItem('accessToken', response.accessToken);
          if (error.config) {
            error.config.headers.Authorization = `Bearer ${response.accessToken}`;
            return this.client.request(error.config);
          }
        } catch {
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  };

  // Auth endpoints
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await this.client.post<AuthResponse>('/auth/login', credentials);
    return response.data;
  }

  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await this.client.post<AuthResponse>('/auth/register', data);
    return response.data;
  }

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const response = await this.client.post<AuthResponse>('/auth/refresh', { refreshToken });
    return response.data;
  }

  // Game endpoints
  async getAvailableGames(page = 0, size = 10): Promise<PagedResponse<Game>> {
    const response = await this.client.get<PagedResponse<Game>>('/games', {
      params: {
        page,
        size,
        status: 'SCHEDULED,ACTIVE', // Only show joinable games
      },
    });
    return response.data;
  }

  async getGame(id: string): Promise<Game> {
    const response = await this.client.get<Game>(`/games/${id}`);
    return response.data;
  }

  async joinGame(gameId: string): Promise<void> {
    await this.client.post(`/games/${gameId}/join`);
  }

  async leaveGame(gameId: string): Promise<void> {
    await this.client.post(`/games/${gameId}/leave`);
  }

  // Question endpoints
  async getActiveQuestion(gameId: string): Promise<Question | null> {
    try {
      const response = await this.client.get<Question>(`/games/${gameId}/questions/active`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  async submitAnswer(
    gameId: string,
    questionId: string,
    selectedOptionIndex: number
  ): Promise<AnswerResult> {
    const response = await this.client.post<AnswerResult>(
      `/games/${gameId}/questions/${questionId}/submit`,
      { selectedOptionIndex }
    );
    return response.data;
  }

  // Leaderboard endpoints
  async getGameLeaderboard(gameId: string, limit = 100): Promise<LeaderboardEntry[]> {
    const response = await this.client.get<LeaderboardEntry[]>(
      `/leaderboards/games/${gameId}`,
      { params: { limit } }
    );
    return response.data;
  }

  async getPlayerStats(gameId: string, userId: string): Promise<PlayerGameStats> {
    const response = await this.client.get<PlayerGameStats>(
      `/leaderboards/users/${userId}/games/${gameId}`
    );
    return response.data;
  }
}

export const api = new ApiService();

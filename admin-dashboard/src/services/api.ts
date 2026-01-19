import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import type {
  Game,
  Question,
  ScheduledGame,
  User,
  LeaderboardEntry,
  AuthResponse,
  LoginRequest,
  PagedResponse
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
      // Try to refresh token
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        try {
          const response = await this.refreshToken(refreshToken);
          localStorage.setItem('accessToken', response.accessToken);
          // Retry original request
          if (error.config) {
            error.config.headers.Authorization = `Bearer ${response.accessToken}`;
            return this.client.request(error.config);
          }
        } catch {
          // Refresh failed, redirect to login
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

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const response = await this.client.post<AuthResponse>('/auth/refresh', { refreshToken });
    return response.data;
  }

  async register(data: { username: string; password: string; displayName: string }): Promise<AuthResponse> {
    const response = await this.client.post<AuthResponse>('/auth/register', data);
    return response.data;
  }

  // Game endpoints
  async getGames(page = 0, size = 10): Promise<PagedResponse<Game>> {
    const response = await this.client.get<PagedResponse<Game>>('/games', {
      params: { page, size },
    });
    return response.data;
  }

  async getGame(id: string): Promise<Game> {
    const response = await this.client.get<Game>(`/games/${id}`);
    return response.data;
  }

  async createGame(game: Partial<Game>): Promise<Game> {
    const response = await this.client.post<Game>('/games', game);
    return response.data;
  }

  async updateGame(id: string, game: Partial<Game>): Promise<Game> {
    const response = await this.client.put<Game>(`/games/${id}`, game);
    return response.data;
  }

  async deleteGame(id: string): Promise<void> {
    await this.client.delete(`/games/${id}`);
  }

  async startGame(id: string): Promise<Game> {
    const response = await this.client.post<Game>(`/games/${id}/start`);
    return response.data;
  }

  async endGame(id: string): Promise<Game> {
    const response = await this.client.post<Game>(`/games/${id}/end`);
    return response.data;
  }

  // Question endpoints
  async getQuestions(gameId: string): Promise<Question[]> {
    const response = await this.client.get<Question[]>(`/games/${gameId}/questions`);
    return response.data;
  }

  async createQuestion(gameId: string, question: Partial<Question>): Promise<Question> {
    const response = await this.client.post<Question>(`/games/${gameId}/questions`, question);
    return response.data;
  }

  async updateQuestion(gameId: string, questionId: string, question: Partial<Question>): Promise<Question> {
    const response = await this.client.put<Question>(`/games/${gameId}/questions/${questionId}`, question);
    return response.data;
  }

  async deleteQuestion(gameId: string, questionId: string): Promise<void> {
    await this.client.delete(`/games/${gameId}/questions/${questionId}`);
  }

  async activateQuestion(gameId: string, questionId: string): Promise<void> {
    await this.client.post(`/games/${gameId}/questions/${questionId}/activate`);
  }

  // Schedule endpoints
  async getSchedules(page = 0, size = 10): Promise<PagedResponse<ScheduledGame>> {
    const response = await this.client.get<PagedResponse<ScheduledGame>>('/schedules', {
      params: { page, size },
    });
    return response.data;
  }

  async createSchedule(schedule: Partial<ScheduledGame>): Promise<ScheduledGame> {
    const response = await this.client.post<ScheduledGame>('/schedules', schedule);
    return response.data;
  }

  async cancelSchedule(id: string): Promise<void> {
    await this.client.delete(`/schedules/${id}`);
  }

  // Leaderboard endpoints
  async getLeaderboard(gameId: string, limit = 100): Promise<LeaderboardEntry[]> {
    const response = await this.client.get<LeaderboardEntry[]>(`/games/${gameId}/leaderboard`, {
      params: { limit },
    });
    return response.data;
  }

  // User endpoints (admin only)
  async getUsers(page = 0, size = 10): Promise<PagedResponse<User>> {
    const response = await this.client.get<PagedResponse<User>>('/admin/users', {
      params: { page, size },
    });
    return response.data;
  }

  async updateUserRole(userId: string, role: string): Promise<User> {
    const response = await this.client.put<User>(`/admin/users/${userId}/role`, { role });
    return response.data;
  }

  // Metrics endpoints
  async getMetrics(): Promise<Record<string, number>> {
    const response = await this.client.get<Record<string, number>>('/actuator/prometheus');
    return response.data;
  }
}

export const api = new ApiService();

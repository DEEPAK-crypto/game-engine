import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { api } from '@/services/api';
import { websocketService } from '@/services/websocket';
import type { LoginRequest, RegisterRequest } from '@/types';

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  username: string | null;
  displayName: string | null;
  roles: string[];
  accessToken: string | null;
  refreshToken: string | null;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  isPlayer: () => boolean;
}

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      isAuthenticated: false,
      userId: null,
      username: null,
      displayName: null,
      roles: [],
      accessToken: null,
      refreshToken: null,

      login: async (credentials: LoginRequest) => {
        const response = await api.login(credentials);

        localStorage.setItem('accessToken', response.accessToken);
        localStorage.setItem('refreshToken', response.refreshToken);

        set({
          isAuthenticated: true,
          userId: response.userId,
          username: response.username,
          displayName: response.username, // May be overridden by API
          roles: response.roles,
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
        });

        // Connect WebSocket after login
        websocketService.connect(response.accessToken);
      },

      register: async (data: RegisterRequest) => {
        const response = await api.register(data);

        localStorage.setItem('accessToken', response.accessToken);
        localStorage.setItem('refreshToken', response.refreshToken);

        set({
          isAuthenticated: true,
          userId: response.userId,
          username: response.username,
          displayName: data.displayName,
          roles: response.roles,
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
        });

        // Connect WebSocket after registration
        websocketService.connect(response.accessToken);
      },

      logout: () => {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');

        websocketService.disconnect();

        set({
          isAuthenticated: false,
          userId: null,
          username: null,
          displayName: null,
          roles: [],
          accessToken: null,
          refreshToken: null,
        });
      },

      isPlayer: () => {
        return get().roles.includes('PLAYER') || get().roles.length === 0;
      },
    }),
    {
      name: 'player-auth-storage',
      partialize: (state) => ({
        isAuthenticated: state.isAuthenticated,
        userId: state.userId,
        username: state.username,
        displayName: state.displayName,
        roles: state.roles,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
      }),
    }
  )
);

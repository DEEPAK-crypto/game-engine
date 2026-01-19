import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { api } from '@/services/api';
import { websocketService } from '@/services/websocket';
import type { LoginRequest } from '@/types';

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  username: string | null;
  roles: string[];
  accessToken: string | null;
  refreshToken: string | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
  hasRole: (role: string) => boolean;
  isAdmin: () => boolean;
  isHost: () => boolean;
}

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      isAuthenticated: false,
      userId: null,
      username: null,
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
          roles: response.roles,
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
        });

        // Connect WebSocket after login
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
          roles: [],
          accessToken: null,
          refreshToken: null,
        });
      },

      hasRole: (role: string) => {
        return get().roles.includes(role);
      },

      isAdmin: () => {
        return get().roles.includes('ADMIN');
      },

      isHost: () => {
        const roles = get().roles;
        return roles.includes('HOST') || roles.includes('ADMIN');
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        isAuthenticated: state.isAuthenticated,
        userId: state.userId,
        username: state.username,
        roles: state.roles,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
      }),
    }
  )
);

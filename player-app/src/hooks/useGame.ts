import { useQuery } from '@tanstack/react-query';
import { api } from '@/services/api';
import type { Game, PagedResponse } from '@/types';

export function useAvailableGames(page = 0, size = 10) {
  return useQuery<PagedResponse<Game>>({
    queryKey: ['games', 'available', page, size],
    queryFn: () => api.getAvailableGames(page, size),
    refetchInterval: 10000, // Refresh every 10 seconds
  });
}

export function useGameDetails(gameId: string | undefined) {
  return useQuery<Game>({
    queryKey: ['games', gameId],
    queryFn: () => api.getGame(gameId!),
    enabled: !!gameId,
  });
}

export function useActiveQuestion(gameId: string | undefined) {
  return useQuery({
    queryKey: ['games', gameId, 'activeQuestion'],
    queryFn: () => api.getActiveQuestion(gameId!),
    enabled: !!gameId,
    refetchInterval: false, // We'll get updates via WebSocket
  });
}

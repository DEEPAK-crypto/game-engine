import { useQuery } from '@tanstack/react-query';
import { api } from '@/services/api';
import type { LeaderboardEntry, PlayerGameStats } from '@/types';

export function useGameLeaderboard(gameId: string | undefined, limit = 100) {
  return useQuery<LeaderboardEntry[]>({
    queryKey: ['leaderboard', gameId, limit],
    queryFn: () => api.getGameLeaderboard(gameId!, limit),
    enabled: !!gameId,
  });
}

export function usePlayerStats(gameId: string | undefined, userId: string | undefined) {
  return useQuery<PlayerGameStats>({
    queryKey: ['playerStats', gameId, userId],
    queryFn: () => api.getPlayerStats(gameId!, userId!),
    enabled: !!gameId && !!userId,
  });
}

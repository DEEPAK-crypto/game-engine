import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services/api';
import type { Game } from '@/types';

export function useGames(page = 0, size = 10) {
  return useQuery({
    queryKey: ['games', page, size],
    queryFn: () => api.getGames(page, size),
  });
}

export function useGame(id: string) {
  return useQuery({
    queryKey: ['game', id],
    queryFn: () => api.getGame(id),
    enabled: !!id,
  });
}

export function useCreateGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (game: Partial<Game>) => api.createGame(game),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['games'] });
    },
  });
}

export function useUpdateGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, game }: { id: string; game: Partial<Game> }) =>
      api.updateGame(id, game),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['games'] });
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });
}

export function useDeleteGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.deleteGame(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['games'] });
    },
  });
}

export function useStartGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.startGame(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['games'] });
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });
}

export function useEndGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.endGame(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['games'] });
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });
}

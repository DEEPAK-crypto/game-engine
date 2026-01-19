import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus,
  Calendar,
  Clock,
  X,
  Loader2,
  Play,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Trash2,
} from 'lucide-react';
import { format } from 'date-fns';
import { api } from '@/services/api';
import type { ScheduledGame, ScheduleStatus } from '@/types';

interface CreateScheduleForm {
  gameId: string;
  scheduledStartTime: string;
  scheduledEndTime?: string;
}

const statusConfig: Record<ScheduleStatus, { icon: React.ElementType; color: string; bg: string }> = {
  PENDING: { icon: Clock, color: 'text-yellow-600', bg: 'bg-yellow-100' },
  STARTED: { icon: Play, color: 'text-green-600', bg: 'bg-green-100' },
  COMPLETED: { icon: CheckCircle, color: 'text-blue-600', bg: 'bg-blue-100' },
  CANCELLED: { icon: XCircle, color: 'text-gray-600', bg: 'bg-gray-100' },
  FAILED: { icon: AlertTriangle, color: 'text-red-600', bg: 'bg-red-100' },
};

export function Schedules() {
  const [page, setPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const queryClient = useQueryClient();

  const { data: schedulesData, isLoading: schedulesLoading } = useQuery({
    queryKey: ['schedules', page],
    queryFn: () => api.getSchedules(page, 10),
  });

  const { data: gamesData } = useQuery({
    queryKey: ['games', 0, 100],
    queryFn: () => api.getGames(0, 100),
  });

  const createSchedule = useMutation({
    mutationFn: (data: Partial<ScheduledGame>) => api.createSchedule(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedules'] });
      setShowCreateModal(false);
      reset();
    },
  });

  const cancelSchedule = useMutation({
    mutationFn: (id: string) => api.cancelSchedule(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['schedules'] });
    },
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateScheduleForm>();

  const schedules = schedulesData?.content || [];
  const games = gamesData?.content || [];
  const draftGames = games.filter((g) => g.status === 'DRAFT');

  const onCreateSchedule = (data: CreateScheduleForm) => {
    createSchedule.mutate({
      gameId: data.gameId,
      scheduledStartTime: new Date(data.scheduledStartTime).toISOString(),
      scheduledEndTime: data.scheduledEndTime
        ? new Date(data.scheduledEndTime).toISOString()
        : undefined,
    });
  };

  const handleCancel = (id: string) => {
    if (window.confirm('Are you sure you want to cancel this scheduled game?')) {
      cancelSchedule.mutate(id);
    }
  };

  const getGameTitle = (gameId: string) => {
    const game = games.find((g) => g.id === gameId);
    return game?.title || 'Unknown Game';
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Scheduled Games</h1>
          <p className="text-gray-500">Manage automated game schedules</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          Schedule Game
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-yellow-100 rounded-lg">
              <Clock className="w-5 h-5 text-yellow-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Pending</p>
              <p className="text-2xl font-bold">
                {schedules.filter((s) => s.status === 'PENDING').length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-green-100 rounded-lg">
              <Play className="w-5 h-5 text-green-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Started</p>
              <p className="text-2xl font-bold">
                {schedules.filter((s) => s.status === 'STARTED').length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 rounded-lg">
              <CheckCircle className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Completed</p>
              <p className="text-2xl font-bold">
                {schedules.filter((s) => s.status === 'COMPLETED').length}
              </p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-red-100 rounded-lg">
              <AlertTriangle className="w-5 h-5 text-red-600" />
            </div>
            <div>
              <p className="text-sm text-gray-500">Failed</p>
              <p className="text-2xl font-bold">
                {schedules.filter((s) => s.status === 'FAILED').length}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Schedules List */}
      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        {schedulesLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
          </div>
        ) : schedules.length === 0 ? (
          <div className="text-center py-12">
            <Calendar className="w-12 h-12 text-gray-300 mx-auto mb-4" />
            <p className="text-gray-500">No scheduled games</p>
            <p className="text-sm text-gray-400 mt-1">
              Schedule a game to have it start automatically
            </p>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Game
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Status
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Scheduled Start
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Scheduled End
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Created
                </th>
                <th className="px-6 py-4 text-right text-sm font-semibold text-gray-600">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {schedules.map((schedule) => {
                const StatusIcon = statusConfig[schedule.status].icon;
                return (
                  <tr key={schedule.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4">
                      <p className="font-medium text-gray-900">
                        {getGameTitle(schedule.gameId)}
                      </p>
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium ${statusConfig[schedule.status].bg} ${statusConfig[schedule.status].color}`}
                      >
                        <StatusIcon className="w-4 h-4" />
                        {schedule.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-600">
                      {format(new Date(schedule.scheduledStartTime), 'MMM d, yyyy HH:mm')}
                    </td>
                    <td className="px-6 py-4 text-gray-600">
                      {schedule.scheduledEndTime
                        ? format(new Date(schedule.scheduledEndTime), 'MMM d, yyyy HH:mm')
                        : '-'}
                    </td>
                    <td className="px-6 py-4 text-gray-500 text-sm">
                      {format(new Date(schedule.createdAt), 'MMM d, yyyy')}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex justify-end">
                        {schedule.status === 'PENDING' && (
                          <button
                            onClick={() => handleCancel(schedule.id)}
                            className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-gray-100"
                            title="Cancel Schedule"
                          >
                            <Trash2 className="w-5 h-5" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {schedulesData && schedulesData.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t">
            <p className="text-sm text-gray-500">
              Page {page + 1} of {schedulesData.totalPages}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-3 py-1 border rounded hover:bg-gray-50 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => setPage(Math.min(schedulesData.totalPages - 1, page + 1))}
                disabled={page >= schedulesData.totalPages - 1}
                className="px-3 py-1 border rounded hover:bg-gray-50 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4">
            <div className="flex items-center justify-between px-6 py-4 border-b">
              <h2 className="text-lg font-semibold">Schedule Game</h2>
              <button
                onClick={() => {
                  setShowCreateModal(false);
                  reset();
                }}
                className="p-2 hover:bg-gray-100 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSubmit(onCreateSchedule)} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Game
                </label>
                <select
                  {...register('gameId', { required: 'Please select a game' })}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                >
                  <option value="">Select a game...</option>
                  {draftGames.map((game) => (
                    <option key={game.id} value={game.id}>
                      {game.title}
                    </option>
                  ))}
                </select>
                {errors.gameId && (
                  <p className="mt-1 text-sm text-red-500">{errors.gameId.message}</p>
                )}
                {draftGames.length === 0 && (
                  <p className="mt-1 text-sm text-yellow-600">
                    No draft games available. Create a game first.
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Start Time
                </label>
                <input
                  type="datetime-local"
                  {...register('scheduledStartTime', { required: 'Start time is required' })}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
                {errors.scheduledStartTime && (
                  <p className="mt-1 text-sm text-red-500">
                    {errors.scheduledStartTime.message}
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  End Time (optional)
                </label>
                <input
                  type="datetime-local"
                  {...register('scheduledEndTime')}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
                <p className="mt-1 text-sm text-gray-500">
                  Leave empty for no automatic end
                </p>
              </div>

              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowCreateModal(false);
                    reset();
                  }}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createSchedule.isPending || draftGames.length === 0}
                  className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
                >
                  {createSchedule.isPending ? 'Scheduling...' : 'Schedule Game'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

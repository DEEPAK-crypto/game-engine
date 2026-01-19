import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import {
  Plus,
  Search,
  MoreVertical,
  Play,
  Square,
  Trash2,
  Edit,
  Eye,
  X,
  Loader2,
  Users,
} from 'lucide-react';
import { useGames, useCreateGame, useDeleteGame, useStartGame, useEndGame } from '@/hooks/useGames';
import type { GameStatus } from '@/types';

const statusColors: Record<GameStatus, { bg: string; text: string }> = {
  DRAFT: { bg: 'bg-gray-100', text: 'text-gray-700' },
  SCHEDULED: { bg: 'bg-yellow-100', text: 'text-yellow-700' },
  ACTIVE: { bg: 'bg-green-100', text: 'text-green-700' },
  PAUSED: { bg: 'bg-orange-100', text: 'text-orange-700' },
  COMPLETED: { bg: 'bg-blue-100', text: 'text-blue-700' },
  CANCELLED: { bg: 'bg-red-100', text: 'text-red-700' },
};

interface CreateGameForm {
  title: string;
  description: string;
  maxPlayers: number;
}

export function Games() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [activeMenu, setActiveMenu] = useState<string | null>(null);

  const { data, isLoading, error } = useGames(page, 10);
  const createGame = useCreateGame();
  const deleteGame = useDeleteGame();
  const startGame = useStartGame();
  const endGame = useEndGame();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateGameForm>();

  const games = data?.content || [];
  const filteredGames = search
    ? games.filter((g) =>
        g.title.toLowerCase().includes(search.toLowerCase())
      )
    : games;

  const onCreateGame = async (formData: CreateGameForm) => {
    try {
      await createGame.mutateAsync({
        title: formData.title,
        description: formData.description,
        maxPlayers: formData.maxPlayers,
        status: 'DRAFT',
      });
      setShowCreateModal(false);
      reset();
    } catch (err) {
      console.error('Failed to create game:', err);
    }
  };

  const handleDelete = async (id: string) => {
    if (window.confirm('Are you sure you want to delete this game?')) {
      await deleteGame.mutateAsync(id);
    }
    setActiveMenu(null);
  };

  const handleStart = async (id: string) => {
    await startGame.mutateAsync(id);
    setActiveMenu(null);
  };

  const handleEnd = async (id: string) => {
    await endGame.mutateAsync(id);
    setActiveMenu(null);
  };

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4">
        <p className="text-red-600">Failed to load games</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Games</h1>
          <p className="text-gray-500">Manage your trivia games</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          Create Game
        </button>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
        <input
          type="text"
          placeholder="Search games..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:border-primary-500"
        />
      </div>

      {/* Games Table */}
      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
          </div>
        ) : filteredGames.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-gray-500">No games found</p>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Title
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Status
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-gray-600">
                  Players
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
              {filteredGames.map((game) => (
                <tr key={game.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-medium text-gray-900">{game.title}</p>
                      <p className="text-sm text-gray-500 truncate max-w-xs">
                        {game.description}
                      </p>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${
                        statusColors[game.status].bg
                      } ${statusColors[game.status].text}`}
                    >
                      {game.status}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-1 text-gray-600">
                      <Users className="w-4 h-4" />
                      <span>
                        {game.currentPlayers}/{game.maxPlayers}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-gray-500 text-sm">
                    {new Date(game.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center justify-end gap-2">
                      <Link
                        to={`/games/${game.id}`}
                        className="p-2 text-gray-400 hover:text-primary-600 rounded-lg hover:bg-gray-100"
                        title="View Details"
                      >
                        <Eye className="w-5 h-5" />
                      </Link>
                      <div className="relative">
                        <button
                          onClick={() =>
                            setActiveMenu(activeMenu === game.id ? null : game.id)
                          }
                          className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
                        >
                          <MoreVertical className="w-5 h-5" />
                        </button>
                        {activeMenu === game.id && (
                          <div className="absolute right-0 top-full mt-1 w-48 bg-white rounded-lg shadow-lg border z-10">
                            {game.status === 'DRAFT' && (
                              <button
                                onClick={() => handleStart(game.id)}
                                className="flex items-center gap-2 w-full px-4 py-2 text-left hover:bg-gray-50"
                              >
                                <Play className="w-4 h-4 text-green-600" />
                                Start Game
                              </button>
                            )}
                            {game.status === 'ACTIVE' && (
                              <button
                                onClick={() => handleEnd(game.id)}
                                className="flex items-center gap-2 w-full px-4 py-2 text-left hover:bg-gray-50"
                              >
                                <Square className="w-4 h-4 text-orange-600" />
                                End Game
                              </button>
                            )}
                            <Link
                              to={`/games/${game.id}`}
                              className="flex items-center gap-2 w-full px-4 py-2 text-left hover:bg-gray-50"
                            >
                              <Edit className="w-4 h-4 text-blue-600" />
                              Edit
                            </Link>
                            {game.status === 'DRAFT' && (
                              <button
                                onClick={() => handleDelete(game.id)}
                                className="flex items-center gap-2 w-full px-4 py-2 text-left text-red-600 hover:bg-red-50"
                              >
                                <Trash2 className="w-4 h-4" />
                                Delete
                              </button>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t">
            <p className="text-sm text-gray-500">
              Page {page + 1} of {data.totalPages}
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
                onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                disabled={page >= data.totalPages - 1}
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
              <h2 className="text-lg font-semibold">Create New Game</h2>
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
            <form onSubmit={handleSubmit(onCreateGame)} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Title
                </label>
                <input
                  type="text"
                  {...register('title', { required: 'Title is required' })}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                  placeholder="Enter game title"
                />
                {errors.title && (
                  <p className="mt-1 text-sm text-red-500">{errors.title.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Description
                </label>
                <textarea
                  {...register('description')}
                  rows={3}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                  placeholder="Enter game description"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Max Players
                </label>
                <input
                  type="number"
                  {...register('maxPlayers', {
                    required: 'Max players is required',
                    min: { value: 2, message: 'Minimum 2 players' },
                    max: { value: 10000, message: 'Maximum 10000 players' },
                  })}
                  defaultValue={100}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
                {errors.maxPlayers && (
                  <p className="mt-1 text-sm text-red-500">
                    {errors.maxPlayers.message}
                  </p>
                )}
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
                  disabled={createGame.isPending}
                  className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
                >
                  {createGame.isPending ? 'Creating...' : 'Create Game'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

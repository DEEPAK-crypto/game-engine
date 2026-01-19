import { useNavigate } from 'react-router-dom';
import { format, formatDistanceToNow } from 'date-fns';
import { Users, Clock, Play, Calendar, Loader2, RefreshCw, Gamepad2 } from 'lucide-react';
import { useAvailableGames } from '@/hooks/useGame';
import type { Game, GameStatus } from '@/types';

function GameStatusBadge({ status }: { status: GameStatus }) {
  const statusConfig = {
    SCHEDULED: { label: 'Upcoming', className: 'bg-blue-100 text-blue-700' },
    ACTIVE: { label: 'Live Now', className: 'bg-green-100 text-green-700' },
    DRAFT: { label: 'Draft', className: 'bg-gray-100 text-gray-700' },
    PAUSED: { label: 'Paused', className: 'bg-yellow-100 text-yellow-700' },
    COMPLETED: { label: 'Ended', className: 'bg-gray-100 text-gray-600' },
    CANCELLED: { label: 'Cancelled', className: 'bg-red-100 text-red-700' },
  };

  const config = statusConfig[status] || statusConfig.DRAFT;

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${config.className}`}>
      {status === 'ACTIVE' && (
        <span className="w-1.5 h-1.5 mr-1.5 bg-green-500 rounded-full animate-pulse" />
      )}
      {config.label}
    </span>
  );
}

function GameCard({ game, onJoin }: { game: Game; onJoin: () => void }) {
  const isActive = game.status === 'ACTIVE';
  const isScheduled = game.status === 'SCHEDULED';
  const canJoin = isActive || isScheduled;

  const formatStartTime = (time: string | undefined) => {
    if (!time) return 'TBD';
    const date = new Date(time);
    const now = new Date();

    if (date > now) {
      return formatDistanceToNow(date, { addSuffix: true });
    }
    return format(date, 'h:mm a');
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md transition-shadow">
      <div className="p-6">
        {/* Header */}
        <div className="flex items-start justify-between mb-3">
          <h3 className="text-lg font-semibold text-gray-900 line-clamp-1">
            {game.title}
          </h3>
          <GameStatusBadge status={game.status} />
        </div>

        {/* Description */}
        {game.description && (
          <p className="text-gray-600 text-sm line-clamp-2 mb-4">
            {game.description}
          </p>
        )}

        {/* Stats */}
        <div className="flex items-center gap-4 text-sm text-gray-500 mb-4">
          <div className="flex items-center gap-1.5">
            <Users className="w-4 h-4" />
            <span>
              {game.currentPlayers}/{game.maxPlayers}
            </span>
          </div>
          {game.scheduledStartTime && (
            <div className="flex items-center gap-1.5">
              <Clock className="w-4 h-4" />
              <span>{formatStartTime(game.scheduledStartTime)}</span>
            </div>
          )}
        </div>

        {/* Action */}
        <button
          onClick={onJoin}
          disabled={!canJoin}
          className={`
            w-full py-3 px-4 rounded-lg font-medium
            flex items-center justify-center gap-2
            transition-colors duration-200
            ${canJoin
              ? isActive
                ? 'bg-green-600 hover:bg-green-700 text-white'
                : 'bg-primary-600 hover:bg-primary-700 text-white'
              : 'bg-gray-100 text-gray-400 cursor-not-allowed'}
          `}
        >
          {isActive ? (
            <>
              <Play className="w-5 h-5" />
              Join Live Game
            </>
          ) : isScheduled ? (
            <>
              <Calendar className="w-5 h-5" />
              Join Lobby
            </>
          ) : (
            'Unavailable'
          )}
        </button>
      </div>
    </div>
  );
}

export function Home() {
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch, isFetching } = useAvailableGames();

  const games = data?.content || [];

  const handleJoinGame = (game: Game) => {
    if (game.status === 'ACTIVE') {
      navigate(`/game/${game.id}/play`);
    } else {
      navigate(`/game/${game.id}/lobby`);
    }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Available Games</h1>
          <p className="text-gray-600 mt-1">Join a trivia game and compete with other players</p>
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-2 px-4 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
        >
          <RefreshCw className={`w-5 h-5 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="flex flex-col items-center justify-center py-16">
          <Loader2 className="w-8 h-8 text-primary-600 animate-spin mb-4" />
          <p className="text-gray-600">Loading games...</p>
        </div>
      )}

      {/* Error State */}
      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
          <p className="text-red-600 mb-4">Failed to load games</p>
          <button
            onClick={() => refetch()}
            className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
          >
            Try Again
          </button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !isError && games.length === 0 && (
        <div className="bg-gray-50 rounded-xl p-12 text-center">
          <div className="w-16 h-16 bg-gray-200 rounded-full flex items-center justify-center mx-auto mb-4">
            <Gamepad2 className="w-8 h-8 text-gray-400" />
          </div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">No games available</h3>
          <p className="text-gray-600 mb-4">Check back later for upcoming trivia games</p>
          <button
            onClick={() => refetch()}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
          >
            Refresh
          </button>
        </div>
      )}

      {/* Games Grid */}
      {!isLoading && games.length > 0 && (
        <div className="grid gap-6 md:grid-cols-2">
          {games.map((game) => (
            <GameCard key={game.id} game={game} onJoin={() => handleJoinGame(game)} />
          ))}
        </div>
      )}

      {/* Auto-refresh indicator */}
      <div className="mt-8 text-center text-sm text-gray-500">
        Games list refreshes automatically every 10 seconds
      </div>
    </div>
  );
}

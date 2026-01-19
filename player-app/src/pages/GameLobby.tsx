import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { Users, Clock, ArrowLeft, Loader2 } from 'lucide-react';
import { useGameDetails } from '@/hooks/useGame';
import { useGame } from '@/stores/game';
import { useAuth } from '@/stores/auth';
import { websocketService } from '@/services/websocket';

export function GameLobby() {
  const { id: gameId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const { data: game, isLoading, isError } = useGameDetails(gameId);

  const {
    status,
    playerCount,
    countdownSeconds,
    setGame,
    handleGameEvent,
    resetGame,
  } = useGame();

  // Set up game state and WebSocket connection
  useEffect(() => {
    if (!gameId || !game) return;

    // Initialize game in store
    setGame(gameId, game.title);

    // Ensure WebSocket is connected
    if (!websocketService.isConnected() && accessToken) {
      websocketService.connect(accessToken);
    }

    // Subscribe to game events
    const unsubscribe = websocketService.subscribeToGame(gameId, handleGameEvent);

    // Join the game via WebSocket
    const unsubscribeConnect = websocketService.onConnect(() => {
      websocketService.joinGame(gameId);
    });

    // If already connected, join immediately
    if (websocketService.isConnected()) {
      websocketService.joinGame(gameId);
    }

    return () => {
      unsubscribe();
      unsubscribeConnect();
      websocketService.leaveGame(gameId);
    };
  }, [gameId, game, accessToken, setGame, handleGameEvent]);

  // Navigate to play screen when game starts
  useEffect(() => {
    if (status === 'playing' || status === 'question') {
      navigate(`/game/${gameId}/play`, { replace: true });
    }
  }, [status, gameId, navigate]);

  const handleBack = () => {
    resetGame();
    navigate('/');
  };

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 text-primary-600 animate-spin mx-auto mb-4" />
          <p className="text-gray-600">Loading game...</p>
        </div>
      </div>
    );
  }

  if (isError || !game) {
    return (
      <div className="max-w-md mx-auto px-4 py-16">
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center">
          <p className="text-red-600 mb-4">Failed to load game</p>
          <button
            onClick={handleBack}
            className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
          >
            Back to Games
          </button>
        </div>
      </div>
    );
  }

  // If game is already active, redirect to play
  if (game.status === 'ACTIVE') {
    navigate(`/game/${gameId}/play`, { replace: true });
    return null;
  }

  // If game is not joinable
  if (game.status !== 'SCHEDULED') {
    return (
      <div className="max-w-md mx-auto px-4 py-16">
        <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 text-center">
          <p className="text-yellow-700 mb-4">This game is not available to join</p>
          <button
            onClick={handleBack}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
          >
            Back to Games
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-8">
      {/* Back Button */}
      <button
        onClick={handleBack}
        className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-6 transition-colors"
      >
        <ArrowLeft className="w-5 h-5" />
        Back to Games
      </button>

      {/* Game Card */}
      <div className="bg-white rounded-2xl shadow-lg overflow-hidden">
        {/* Header */}
        <div className="bg-gradient-to-r from-primary-600 to-primary-700 px-6 py-8 text-center text-white">
          <h1 className="text-2xl font-bold mb-2">{game.title}</h1>
          {game.description && (
            <p className="text-primary-100">{game.description}</p>
          )}
        </div>

        {/* Content */}
        <div className="p-6">
          {/* Stats */}
          <div className="flex justify-center gap-8 mb-8">
            <div className="text-center">
              <div className="flex items-center justify-center gap-2 text-gray-600 mb-1">
                <Users className="w-5 h-5" />
                <span className="text-sm">Players</span>
              </div>
              <p className="text-2xl font-bold text-gray-900">
                {playerCount || game.currentPlayers}
                <span className="text-gray-400 text-lg">/{game.maxPlayers}</span>
              </p>
            </div>

            {game.scheduledStartTime && (
              <div className="text-center">
                <div className="flex items-center justify-center gap-2 text-gray-600 mb-1">
                  <Clock className="w-5 h-5" />
                  <span className="text-sm">Starts at</span>
                </div>
                <p className="text-2xl font-bold text-gray-900">
                  {format(new Date(game.scheduledStartTime), 'h:mm a')}
                </p>
              </div>
            )}
          </div>

          {/* Countdown */}
          {countdownSeconds !== null && countdownSeconds > 0 && (
            <div className="bg-primary-50 rounded-xl p-6 text-center mb-6">
              <p className="text-primary-600 text-sm font-medium mb-2">Game starting in</p>
              <p className="text-5xl font-bold text-primary-700 animate-countdown">
                {countdownSeconds}
              </p>
              <p className="text-primary-600 text-sm mt-2">Get ready!</p>
            </div>
          )}

          {/* Waiting State */}
          {(countdownSeconds === null || countdownSeconds === 0) && (
            <div className="text-center py-8">
              <div className="relative inline-flex items-center justify-center mb-4">
                <div className="w-16 h-16 bg-primary-100 rounded-full flex items-center justify-center">
                  <div className="w-4 h-4 bg-primary-500 rounded-full animate-pulse" />
                </div>
                <div className="absolute w-24 h-24 bg-primary-200 rounded-full opacity-50 pulse-ring" />
              </div>
              <p className="text-gray-600">Waiting for the game to start...</p>
              <p className="text-gray-400 text-sm mt-2">
                The host will start the game soon
              </p>
            </div>
          )}

          {/* Info */}
          <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-600">
            <p className="font-medium text-gray-700 mb-2">How to play:</p>
            <ul className="space-y-1 list-disc list-inside">
              <li>Answer questions as quickly as possible</li>
              <li>First correct answer wins bonus points</li>
              <li>Watch the leaderboard for your ranking</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

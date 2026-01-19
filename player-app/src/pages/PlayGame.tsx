import { useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Trophy, Send, Loader2 } from 'lucide-react';
import { useGameDetails } from '@/hooks/useGame';
import { useGame } from '@/stores/game';
import { useAuth } from '@/stores/auth';
import { api } from '@/services/api';
import { websocketService } from '@/services/websocket';
import { QuestionCard } from '@/components/QuestionCard';
import { Timer } from '@/components/Timer';
import { Leaderboard } from '@/components/Leaderboard';

export function PlayGame() {
  const { id: gameId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { userId, accessToken } = useAuth();
  const { data: game, isLoading: isLoadingGame } = useGameDetails(gameId);

  const {
    status,
    gameTitle,
    currentQuestion,
    questionNumber,
    totalQuestions,
    expiresAt,
    selectedOption,
    submitted,
    correctOptionIndex,
    leaderboard,
    playerScore,
    playerRank,
    questionWinner,
    setGame,
    selectOption,
    submitAnswer,
    setResult,
    handleGameEvent,
    resetGame,
  } = useGame();

  // Set up WebSocket connection and subscribe to game events
  useEffect(() => {
    if (!gameId) return;

    // Ensure WebSocket is connected
    if (!websocketService.isConnected() && accessToken) {
      websocketService.connect(accessToken);
    }

    // Subscribe to game events
    const unsubscribe = websocketService.subscribeToGame(gameId, handleGameEvent);

    // Join the game via WebSocket when connected
    const unsubscribeConnect = websocketService.onConnect(() => {
      websocketService.joinGame(gameId);
    });

    if (websocketService.isConnected()) {
      websocketService.joinGame(gameId);
    }

    return () => {
      unsubscribe();
      unsubscribeConnect();
    };
  }, [gameId, accessToken, handleGameEvent]);

  // Initialize game state
  useEffect(() => {
    if (game && !gameTitle) {
      setGame(game.id, game.title);
    }
  }, [game, gameTitle, setGame]);

  // Handle game completion - navigate to results
  useEffect(() => {
    if (status === 'completed') {
      // Stay on the page to show final results
    }
  }, [status]);

  const handleSelectOption = useCallback((index: number) => {
    if (!submitted && correctOptionIndex === null) {
      selectOption(index);
    }
  }, [submitted, correctOptionIndex, selectOption]);

  const handleSubmitAnswer = useCallback(async () => {
    if (!gameId || !currentQuestion || selectedOption === null || submitted) return;

    submitAnswer();

    try {
      const result = await api.submitAnswer(gameId, currentQuestion.id, selectedOption);
      setResult(result);
    } catch (error) {
      console.error('Failed to submit answer:', error);
    }
  }, [gameId, currentQuestion, selectedOption, submitted, submitAnswer, setResult]);

  const handleBack = () => {
    resetGame();
    navigate('/');
  };

  // Find current player in leaderboard
  const currentPlayerEntry = leaderboard.find(entry => entry.playerId === userId);

  if (isLoadingGame) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 text-primary-600 animate-spin mx-auto mb-4" />
          <p className="text-gray-600">Loading game...</p>
        </div>
      </div>
    );
  }

  // Game completed view
  if (status === 'completed') {
    return (
      <div className="max-w-2xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="w-20 h-20 bg-yellow-100 rounded-full flex items-center justify-center mx-auto mb-4 celebrate">
            <Trophy className="w-10 h-10 text-yellow-600" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Game Over!</h1>
          <p className="text-gray-600">{gameTitle}</p>
        </div>

        {/* Personal Stats */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Your Results</h2>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <p className="text-3xl font-bold text-primary-600">
                {currentPlayerEntry?.rank || playerRank || '-'}
              </p>
              <p className="text-sm text-gray-500">Final Rank</p>
            </div>
            <div>
              <p className="text-3xl font-bold text-gray-900">
                {currentPlayerEntry?.score || playerScore}
              </p>
              <p className="text-sm text-gray-500">Total Score</p>
            </div>
            <div>
              <p className="text-3xl font-bold text-green-600">
                {currentPlayerEntry?.correctAnswers || 0}/{currentPlayerEntry?.totalAnswers || totalQuestions}
              </p>
              <p className="text-sm text-gray-500">Correct</p>
            </div>
          </div>
        </div>

        {/* Final Leaderboard */}
        <Leaderboard entries={leaderboard} title="Final Standings" showAll />

        {/* Back Button */}
        <button
          onClick={handleBack}
          className="w-full mt-6 py-3 px-4 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors font-medium"
        >
          Back to Games
        </button>
      </div>
    );
  }

  // Waiting for question state
  if (!currentQuestion || status === 'lobby' || status === 'playing' || status === 'waiting') {
    return (
      <div className="max-w-2xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={handleBack}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-900 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
            Leave Game
          </button>
          <div className="text-right">
            <p className="text-sm text-gray-500">Your Score</p>
            <p className="text-xl font-bold text-gray-900">{playerScore}</p>
          </div>
        </div>

        {/* Game Title */}
        <h1 className="text-xl font-bold text-gray-900 mb-6">{gameTitle}</h1>

        {/* Waiting State */}
        <div className="bg-white rounded-2xl shadow-lg p-8 text-center mb-6">
          <div className="relative inline-flex items-center justify-center mb-6">
            <div className="w-20 h-20 bg-primary-100 rounded-full flex items-center justify-center">
              <Loader2 className="w-8 h-8 text-primary-600 animate-spin" />
            </div>
          </div>

          {status === 'waiting' && correctOptionIndex !== null ? (
            <>
              <h2 className="text-xl font-bold text-gray-900 mb-2">
                Question {questionNumber} Complete
              </h2>
              {questionWinner && (
                <div className="bg-yellow-50 rounded-lg p-4 mb-4">
                  <p className="text-yellow-700">
                    <Trophy className="w-5 h-5 inline-block mr-2" />
                    <span className="font-semibold">{questionWinner.name}</span> won {questionWinner.points} points!
                  </p>
                </div>
              )}
              <p className="text-gray-600">Waiting for next question...</p>
            </>
          ) : (
            <>
              <h2 className="text-xl font-bold text-gray-900 mb-2">Get Ready!</h2>
              <p className="text-gray-600">The next question is coming up...</p>
            </>
          )}
        </div>

        {/* Mini Leaderboard */}
        {leaderboard.length > 0 && (
          <Leaderboard entries={leaderboard.slice(0, 5)} title="Top 5" />
        )}
      </div>
    );
  }

  // Active question view
  return (
    <div className="max-w-4xl mx-auto px-4 py-6">
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Main Content */}
        <div className="lg:col-span-2">
          {/* Header */}
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-lg font-bold text-gray-900">{gameTitle}</h1>
            <div className="text-right">
              <p className="text-sm text-gray-500">Score</p>
              <p className="text-xl font-bold text-primary-600">{playerScore}</p>
            </div>
          </div>

          {/* Timer */}
          <div className="flex justify-center mb-6">
            <Timer expiresAt={expiresAt} size="lg" />
          </div>

          {/* Question Card */}
          <QuestionCard
            question={currentQuestion}
            questionNumber={questionNumber}
            totalQuestions={totalQuestions}
            selectedOption={selectedOption}
            submitted={submitted}
            correctOptionIndex={correctOptionIndex}
            onSelectOption={handleSelectOption}
          />

          {/* Submit Button */}
          {!submitted && selectedOption !== null && correctOptionIndex === null && (
            <button
              onClick={handleSubmitAnswer}
              className="w-full mt-4 py-4 px-6 bg-primary-600 text-white rounded-xl hover:bg-primary-700 transition-colors font-semibold flex items-center justify-center gap-2 text-lg"
            >
              <Send className="w-5 h-5" />
              Submit Answer
            </button>
          )}

          {/* Winner Announcement */}
          {questionWinner && correctOptionIndex !== null && (
            <div className="mt-4 bg-yellow-50 border border-yellow-200 rounded-xl p-4 text-center fade-in">
              <Trophy className="w-6 h-6 text-yellow-600 mx-auto mb-2" />
              <p className="text-yellow-700 font-medium">
                <span className="font-bold">{questionWinner.name}</span> won this question!
              </p>
              <p className="text-yellow-600 text-sm">+{questionWinner.points} points</p>
            </div>
          )}
        </div>

        {/* Sidebar - Leaderboard */}
        <div className="hidden lg:block">
          <div className="sticky top-24">
            <Leaderboard entries={leaderboard.slice(0, 10)} />
          </div>
        </div>
      </div>

      {/* Mobile Leaderboard Toggle */}
      <div className="lg:hidden mt-6">
        <details className="bg-white rounded-xl shadow-sm border border-gray-100">
          <summary className="px-4 py-3 cursor-pointer font-medium text-gray-900">
            View Leaderboard ({leaderboard.length} players)
          </summary>
          <div className="border-t border-gray-100">
            <Leaderboard entries={leaderboard.slice(0, 10)} />
          </div>
        </details>
      </div>
    </div>
  );
}

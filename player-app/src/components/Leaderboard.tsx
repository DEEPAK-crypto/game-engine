import { Trophy, Medal, Award, TrendingUp } from 'lucide-react';
import type { LeaderboardEntry } from '@/types';
import { useAuth } from '@/stores/auth';

interface LeaderboardProps {
  entries: LeaderboardEntry[];
  title?: string;
  showAll?: boolean;
}

export function Leaderboard({ entries, title = 'Leaderboard', showAll = false }: LeaderboardProps) {
  const { userId } = useAuth();

  const displayEntries = showAll ? entries : entries.slice(0, 10);

  const getRankIcon = (rank: number) => {
    switch (rank) {
      case 1:
        return <Trophy className="w-5 h-5 text-yellow-500" />;
      case 2:
        return <Medal className="w-5 h-5 text-gray-400" />;
      case 3:
        return <Award className="w-5 h-5 text-amber-600" />;
      default:
        return <span className="w-5 text-center font-bold text-gray-500">{rank}</span>;
    }
  };

  const getRankBadgeClasses = (rank: number) => {
    switch (rank) {
      case 1:
        return 'bg-yellow-50 border-yellow-200';
      case 2:
        return 'bg-gray-50 border-gray-200';
      case 3:
        return 'bg-amber-50 border-amber-200';
      default:
        return 'bg-white border-gray-100';
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
      <div className="px-4 py-3 border-b border-gray-100 flex items-center gap-2">
        <TrendingUp className="w-5 h-5 text-primary-600" />
        <h3 className="font-semibold text-gray-900">{title}</h3>
        <span className="ml-auto text-sm text-gray-500">
          {entries.length} player{entries.length !== 1 ? 's' : ''}
        </span>
      </div>

      {entries.length === 0 ? (
        <div className="p-6 text-center text-gray-500">
          No scores yet
        </div>
      ) : (
        <div className="divide-y divide-gray-50">
          {displayEntries.map((entry) => {
            const isCurrentUser = entry.playerId === userId;

            return (
              <div
                key={entry.playerId}
                className={`
                  leaderboard-row flex items-center gap-3 px-4 py-3
                  ${isCurrentUser ? 'bg-primary-50' : ''}
                `}
              >
                {/* Rank */}
                <div
                  className={`
                    w-8 h-8 flex items-center justify-center rounded-lg border
                    ${getRankBadgeClasses(entry.rank)}
                  `}
                >
                  {getRankIcon(entry.rank)}
                </div>

                {/* Player Name */}
                <div className="flex-1 min-w-0">
                  <p className={`font-medium truncate ${isCurrentUser ? 'text-primary-700' : 'text-gray-900'}`}>
                    {entry.playerName}
                    {isCurrentUser && <span className="text-sm ml-1">(You)</span>}
                  </p>
                  <p className="text-xs text-gray-500">
                    {entry.correctAnswers}/{entry.totalAnswers} correct
                  </p>
                </div>

                {/* Score */}
                <div className="text-right">
                  <p className={`font-bold ${isCurrentUser ? 'text-primary-700' : 'text-gray-900'}`}>
                    {entry.score.toLocaleString()}
                  </p>
                  <p className="text-xs text-gray-500">
                    {Math.round(entry.avgResponseTime)}ms avg
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {!showAll && entries.length > 10 && (
        <div className="px-4 py-2 bg-gray-50 text-center">
          <span className="text-sm text-gray-500">
            +{entries.length - 10} more players
          </span>
        </div>
      )}
    </div>
  );
}

import { useQuery } from '@tanstack/react-query';
import {
  Gamepad2,
  Users,
  Trophy,
  Clock,
  TrendingUp,
  AlertCircle
} from 'lucide-react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
} from 'chart.js';
import { Line, Doughnut } from 'react-chartjs-2';
import { api } from '@/services/api';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement
);

interface StatCardProps {
  title: string;
  value: string | number;
  icon: React.ElementType;
  change?: string;
  changeType?: 'positive' | 'negative' | 'neutral';
}

function StatCard({ title, value, icon: Icon, change, changeType = 'neutral' }: StatCardProps) {
  const changeColors = {
    positive: 'text-green-600',
    negative: 'text-red-600',
    neutral: 'text-gray-500',
  };

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500">{title}</p>
          <p className="text-2xl font-bold mt-1">{value}</p>
          {change && (
            <p className={`text-sm mt-1 ${changeColors[changeType]}`}>
              {change}
            </p>
          )}
        </div>
        <div className="p-3 bg-primary-50 rounded-lg">
          <Icon className="w-6 h-6 text-primary-600" />
        </div>
      </div>
    </div>
  );
}

export function Dashboard() {
  const { data: gamesData } = useQuery({
    queryKey: ['games', 0, 100],
    queryFn: () => api.getGames(0, 100),
  });

  const games = gamesData?.content || [];
  const activeGames = games.filter((g) => g.status === 'ACTIVE').length;
  const totalPlayers = games.reduce((sum, g) => sum + (g.currentPlayers || 0), 0);
  const completedToday = games.filter(
    (g) =>
      g.status === 'COMPLETED' &&
      new Date(g.endTime || '').toDateString() === new Date().toDateString()
  ).length;

  // Mock data for charts
  const activityData = {
    labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    datasets: [
      {
        label: 'Games Played',
        data: [12, 19, 15, 25, 22, 30, 18],
        borderColor: 'rgb(14, 165, 233)',
        backgroundColor: 'rgba(14, 165, 233, 0.1)',
        fill: true,
        tension: 0.4,
      },
    ],
  };

  const statusData = {
    labels: ['Active', 'Scheduled', 'Completed', 'Draft'],
    datasets: [
      {
        data: [
          games.filter((g) => g.status === 'ACTIVE').length,
          games.filter((g) => g.status === 'SCHEDULED').length,
          games.filter((g) => g.status === 'COMPLETED').length,
          games.filter((g) => g.status === 'DRAFT').length,
        ],
        backgroundColor: [
          'rgb(34, 197, 94)',
          'rgb(234, 179, 8)',
          'rgb(59, 130, 246)',
          'rgb(156, 163, 175)',
        ],
      },
    ],
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500">Welcome back! Here's what's happening.</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Active Games"
          value={activeGames}
          icon={Gamepad2}
          change="+2 from yesterday"
          changeType="positive"
        />
        <StatCard
          title="Total Players Online"
          value={totalPlayers}
          icon={Users}
          change="+15% this hour"
          changeType="positive"
        />
        <StatCard
          title="Games Completed Today"
          value={completedToday}
          icon={Trophy}
        />
        <StatCard
          title="Avg Response Time"
          value="124ms"
          icon={Clock}
          change="-8ms from avg"
          changeType="positive"
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl shadow-sm p-6">
          <h2 className="text-lg font-semibold mb-4">Weekly Activity</h2>
          <Line
            data={activityData}
            options={{
              responsive: true,
              plugins: {
                legend: {
                  display: false,
                },
              },
              scales: {
                y: {
                  beginAtZero: true,
                },
              },
            }}
          />
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <h2 className="text-lg font-semibold mb-4">Game Status</h2>
          <Doughnut
            data={statusData}
            options={{
              responsive: true,
              plugins: {
                legend: {
                  position: 'bottom',
                },
              },
            }}
          />
        </div>
      </div>

      {/* Recent Activity */}
      <div className="bg-white rounded-xl shadow-sm p-6">
        <h2 className="text-lg font-semibold mb-4">Recent Activity</h2>
        <div className="space-y-4">
          {games.slice(0, 5).map((game) => (
            <div
              key={game.id}
              className="flex items-center justify-between py-3 border-b last:border-0"
            >
              <div className="flex items-center gap-3">
                <div
                  className={`w-2 h-2 rounded-full ${
                    game.status === 'ACTIVE'
                      ? 'bg-green-500'
                      : game.status === 'SCHEDULED'
                      ? 'bg-yellow-500'
                      : 'bg-gray-400'
                  }`}
                />
                <div>
                  <p className="font-medium">{game.title}</p>
                  <p className="text-sm text-gray-500">
                    {game.currentPlayers} players
                  </p>
                </div>
              </div>
              <span
                className={`
                  px-3 py-1 text-sm rounded-full
                  ${
                    game.status === 'ACTIVE'
                      ? 'bg-green-100 text-green-700'
                      : game.status === 'SCHEDULED'
                      ? 'bg-yellow-100 text-yellow-700'
                      : game.status === 'COMPLETED'
                      ? 'bg-blue-100 text-blue-700'
                      : 'bg-gray-100 text-gray-700'
                  }
                `}
              >
                {game.status}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* System Health */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <TrendingUp className="w-5 h-5 text-green-500" />
            <h3 className="font-semibold">System Health</h3>
          </div>
          <div className="space-y-3">
            <div className="flex justify-between">
              <span className="text-gray-500">API</span>
              <span className="text-green-600 font-medium">Healthy</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Database</span>
              <span className="text-green-600 font-medium">Healthy</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Redis</span>
              <span className="text-green-600 font-medium">Healthy</span>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <AlertCircle className="w-5 h-5 text-yellow-500" />
            <h3 className="font-semibold">Alerts</h3>
          </div>
          <p className="text-gray-500 text-sm">No active alerts</p>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <Clock className="w-5 h-5 text-blue-500" />
            <h3 className="font-semibold">Upcoming</h3>
          </div>
          <p className="text-gray-500 text-sm">
            {games.filter((g) => g.status === 'SCHEDULED').length} scheduled
            games
          </p>
        </div>
      </div>
    </div>
  );
}

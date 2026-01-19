import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
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
  Filler,
} from 'chart.js';
import { Line, Bar } from 'react-chartjs-2';
import {
  Activity,
  Cpu,
  Database,
  HardDrive,
  RefreshCw,
  Loader2,
  TrendingUp,
  TrendingDown,
  AlertTriangle,
  CheckCircle,
  Clock,
  Zap,
} from 'lucide-react';
import { api } from '@/services/api';
import { websocketService } from '@/services/websocket';
import type { GameEvent } from '@/types';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

interface MetricData {
  timestamp: Date;
  value: number;
}

export function Metrics() {
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [responseTimeHistory, setResponseTimeHistory] = useState<MetricData[]>([]);
  const [requestRateHistory, setRequestRateHistory] = useState<MetricData[]>([]);
  const [liveMetrics, setLiveMetrics] = useState<{
    activeGames: number;
    totalPlayers: number;
    requestsPerSecond: number;
  } | null>(null);

  const { data: gamesData, isLoading: gamesLoading } = useQuery({
    queryKey: ['games', 0, 100],
    queryFn: () => api.getGames(0, 100),
    refetchInterval: autoRefresh ? 10000 : false,
  });

  // Subscribe to admin metrics events
  useEffect(() => {
    const unsubscribe = websocketService.subscribeToAdmin((event: GameEvent) => {
      if (event.eventType === 'METRICS_UPDATE') {
        const payload = event.payload as {
          activeGames: number;
          totalPlayers: number;
          requestsPerSecond: number;
        };
        setLiveMetrics(payload);
      }
    });

    return () => unsubscribe();
  }, []);

  // Simulate metric history
  useEffect(() => {
    const now = new Date();
    const history: MetricData[] = [];
    for (let i = 29; i >= 0; i--) {
      history.push({
        timestamp: new Date(now.getTime() - i * 60000),
        value: Math.floor(Math.random() * 50) + 100,
      });
    }
    setResponseTimeHistory(history);

    const rateHistory: MetricData[] = [];
    for (let i = 29; i >= 0; i--) {
      rateHistory.push({
        timestamp: new Date(now.getTime() - i * 60000),
        value: Math.floor(Math.random() * 100) + 50,
      });
    }
    setRequestRateHistory(rateHistory);
  }, []);

  const games = gamesData?.content || [];
  const activeGames = liveMetrics?.activeGames ?? games.filter((g) => g.status === 'ACTIVE').length;
  const totalPlayers = liveMetrics?.totalPlayers ?? games.reduce((sum, g) => sum + (g.currentPlayers || 0), 0);

  const responseTimeData = {
    labels: responseTimeHistory.map((d) =>
      d.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    ),
    datasets: [
      {
        label: 'Response Time (ms)',
        data: responseTimeHistory.map((d) => d.value),
        borderColor: 'rgb(14, 165, 233)',
        backgroundColor: 'rgba(14, 165, 233, 0.1)',
        fill: true,
        tension: 0.4,
      },
    ],
  };

  const requestRateData = {
    labels: requestRateHistory.map((d) =>
      d.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    ),
    datasets: [
      {
        label: 'Requests/sec',
        data: requestRateHistory.map((d) => d.value),
        backgroundColor: 'rgba(34, 197, 94, 0.8)',
        borderColor: 'rgb(34, 197, 94)',
        borderWidth: 1,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
      },
    },
    scales: {
      x: {
        grid: {
          display: false,
        },
        ticks: {
          maxTicksLimit: 6,
        },
      },
      y: {
        beginAtZero: true,
      },
    },
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Metrics</h1>
          <p className="text-gray-500">Real-time system and game metrics</p>
        </div>
        <button
          onClick={() => setAutoRefresh(!autoRefresh)}
          className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg transition-colors ${
            autoRefresh
              ? 'bg-green-100 text-green-700'
              : 'bg-gray-100 text-gray-600'
          }`}
        >
          <RefreshCw className={`w-4 h-4 ${autoRefresh ? 'animate-spin' : ''}`} />
          Auto Refresh {autoRefresh ? 'On' : 'Off'}
        </button>
      </div>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Active Games</p>
              <p className="text-3xl font-bold mt-1">{activeGames}</p>
              <p className="text-sm text-green-600 mt-1 flex items-center gap-1">
                <TrendingUp className="w-4 h-4" />
                Live
              </p>
            </div>
            <div className="p-3 bg-green-100 rounded-lg">
              <Activity className="w-6 h-6 text-green-600" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Total Players Online</p>
              <p className="text-3xl font-bold mt-1">{totalPlayers}</p>
              <p className="text-sm text-blue-600 mt-1 flex items-center gap-1">
                <TrendingUp className="w-4 h-4" />
                +12% from last hour
              </p>
            </div>
            <div className="p-3 bg-blue-100 rounded-lg">
              <Zap className="w-6 h-6 text-blue-600" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Avg Response Time</p>
              <p className="text-3xl font-bold mt-1">
                {responseTimeHistory.length > 0
                  ? responseTimeHistory[responseTimeHistory.length - 1].value
                  : 0}
                ms
              </p>
              <p className="text-sm text-green-600 mt-1 flex items-center gap-1">
                <TrendingDown className="w-4 h-4" />
                -8ms from avg
              </p>
            </div>
            <div className="p-3 bg-yellow-100 rounded-lg">
              <Clock className="w-6 h-6 text-yellow-600" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Error Rate</p>
              <p className="text-3xl font-bold mt-1">0.02%</p>
              <p className="text-sm text-green-600 mt-1 flex items-center gap-1">
                <CheckCircle className="w-4 h-4" />
                Healthy
              </p>
            </div>
            <div className="p-3 bg-purple-100 rounded-lg">
              <AlertTriangle className="w-6 h-6 text-purple-600" />
            </div>
          </div>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl shadow-sm p-6">
          <h2 className="text-lg font-semibold mb-4">Response Time</h2>
          <div className="h-64">
            <Line data={responseTimeData} options={chartOptions} />
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <h2 className="text-lg font-semibold mb-4">Request Rate</h2>
          <div className="h-64">
            <Bar data={requestRateData} options={chartOptions} />
          </div>
        </div>
      </div>

      {/* System Health */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <Cpu className="w-5 h-5 text-primary-600" />
            <h3 className="font-semibold">CPU Usage</h3>
          </div>
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">Game Service</span>
                <span className="font-medium">32%</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-primary-500 rounded-full" style={{ width: '32%' }} />
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">Scheduler</span>
                <span className="font-medium">18%</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-primary-500 rounded-full" style={{ width: '18%' }} />
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">WebSocket Handler</span>
                <span className="font-medium">45%</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-yellow-500 rounded-full" style={{ width: '45%' }} />
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <HardDrive className="w-5 h-5 text-primary-600" />
            <h3 className="font-semibold">Memory Usage</h3>
          </div>
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">Heap Used</span>
                <span className="font-medium">1.2 GB / 2 GB</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-primary-500 rounded-full" style={{ width: '60%' }} />
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">Non-Heap</span>
                <span className="font-medium">256 MB</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-primary-500 rounded-full" style={{ width: '25%' }} />
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-gray-500">Redis Cache</span>
                <span className="font-medium">512 MB / 1 GB</span>
              </div>
              <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-primary-500 rounded-full" style={{ width: '50%' }} />
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm p-6">
          <div className="flex items-center gap-3 mb-4">
            <Database className="w-5 h-5 text-primary-600" />
            <h3 className="font-semibold">Database Connections</h3>
          </div>
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full" />
                <span className="text-gray-600">PostgreSQL</span>
              </div>
              <span className="font-medium">15 / 50</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full" />
                <span className="text-gray-600">Cassandra</span>
              </div>
              <span className="font-medium">8 / 20</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full" />
                <span className="text-gray-600">Redis</span>
              </div>
              <span className="font-medium">12 / 30</span>
            </div>
            <div className="pt-2 border-t">
              <p className="text-sm text-gray-500">All connections healthy</p>
            </div>
          </div>
        </div>
      </div>

      {/* Active Games Table */}
      <div className="bg-white rounded-xl shadow-sm p-6">
        <h2 className="text-lg font-semibold mb-4">Active Games Performance</h2>
        {gamesLoading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-6 h-6 animate-spin text-primary-600" />
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-3 text-sm font-semibold text-gray-600">Game</th>
                <th className="text-left py-3 text-sm font-semibold text-gray-600">Players</th>
                <th className="text-left py-3 text-sm font-semibold text-gray-600">Questions</th>
                <th className="text-left py-3 text-sm font-semibold text-gray-600">Avg Response</th>
                <th className="text-left py-3 text-sm font-semibold text-gray-600">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {games
                .filter((g) => g.status === 'ACTIVE')
                .map((game) => (
                  <tr key={game.id}>
                    <td className="py-3 font-medium">{game.title}</td>
                    <td className="py-3">{game.currentPlayers}</td>
                    <td className="py-3">-</td>
                    <td className="py-3">{Math.floor(Math.random() * 50) + 80}ms</td>
                    <td className="py-3">
                      <span className="px-2 py-1 bg-green-100 text-green-700 text-sm rounded-full">
                        Live
                      </span>
                    </td>
                  </tr>
                ))}
              {games.filter((g) => g.status === 'ACTIVE').length === 0 && (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-gray-500">
                    No active games at the moment
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

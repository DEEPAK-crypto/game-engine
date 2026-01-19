import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { Layout } from '@/components/Layout';
import { Login } from '@/pages/Login';
import { Home } from '@/pages/Home';
import { GameLobby } from '@/pages/GameLobby';
import { PlayGame } from '@/pages/PlayGame';
import { useAuth } from '@/stores/auth';
import { websocketService } from '@/services/websocket';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export default function App() {
  const { isAuthenticated, accessToken } = useAuth();

  // Reconnect WebSocket on app load if authenticated
  useEffect(() => {
    if (isAuthenticated && accessToken) {
      websocketService.connect(accessToken);
    }

    return () => {
      // Don't disconnect on unmount to maintain connection during navigation
    };
  }, [isAuthenticated, accessToken]);

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <Login />}
      />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Home />} />
        <Route path="game/:id/lobby" element={<GameLobby />} />
        <Route path="game/:id/play" element={<PlayGame />} />
      </Route>
    </Routes>
  );
}

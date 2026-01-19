import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { GameEvent } from '@/types';

type EventHandler = (event: GameEvent) => void;

class WebSocketService {
  private client: Client | null = null;
  private handlers: Map<string, Set<EventHandler>> = new Map();
  private connected = false;

  connect(token?: string) {
    if (this.connected) return;

    const wsUrl = import.meta.env.VITE_WS_URL || '/ws';

    this.client = new Client({
      webSocketFactory: () => new SockJS(wsUrl) as WebSocket,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      debug: (str) => {
        if (import.meta.env.DEV) {
          console.log('[WS]', str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = () => {
      this.connected = true;
      console.log('[WS] Connected');
    };

    this.client.onDisconnect = () => {
      this.connected = false;
      console.log('[WS] Disconnected');
    };

    this.client.onStompError = (frame) => {
      console.error('[WS] Error:', frame.headers['message']);
    };

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.connected = false;
      this.handlers.clear();
    }
  }

  subscribeToGame(gameId: string, handler: EventHandler): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WS] Not connected, cannot subscribe');
      return () => {};
    }

    const topic = `/topic/game/${gameId}`;

    // Store handler
    if (!this.handlers.has(topic)) {
      this.handlers.set(topic, new Set());
    }
    this.handlers.get(topic)!.add(handler);

    // Subscribe
    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as GameEvent;
        this.handlers.get(topic)?.forEach((h) => h(event));
      } catch (e) {
        console.error('[WS] Failed to parse message:', e);
      }
    });

    // Return unsubscribe function
    return () => {
      subscription.unsubscribe();
      this.handlers.get(topic)?.delete(handler);
    };
  }

  subscribeToAdmin(handler: EventHandler): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WS] Not connected, cannot subscribe');
      return () => {};
    }

    const topic = '/topic/admin';

    if (!this.handlers.has(topic)) {
      this.handlers.set(topic, new Set());
    }
    this.handlers.get(topic)!.add(handler);

    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as GameEvent;
        this.handlers.get(topic)?.forEach((h) => h(event));
      } catch (e) {
        console.error('[WS] Failed to parse message:', e);
      }
    });

    return () => {
      subscription.unsubscribe();
      this.handlers.get(topic)?.delete(handler);
    };
  }

  joinGame(gameId: string) {
    if (!this.client || !this.connected) return;
    this.client.publish({
      destination: `/app/game/${gameId}/join`,
      body: JSON.stringify({}),
    });
  }

  leaveGame(gameId: string) {
    if (!this.client || !this.connected) return;
    this.client.publish({
      destination: `/app/game/${gameId}/leave`,
      body: JSON.stringify({}),
    });
  }

  isConnected(): boolean {
    return this.connected;
  }
}

export const websocketService = new WebSocketService();

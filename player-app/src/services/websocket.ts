import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { GameEvent } from '@/types';

type EventHandler = (event: GameEvent) => void;
type ConnectionHandler = () => void;

class WebSocketService {
  private client: Client | null = null;
  private handlers: Map<string, Set<EventHandler>> = new Map();
  private connected = false;
  private onConnectHandlers: Set<ConnectionHandler> = new Set();
  private onDisconnectHandlers: Set<ConnectionHandler> = new Set();
  private pendingSubscriptions: Array<{ gameId: string; handler: EventHandler }> = [];

  connect(token?: string) {
    if (this.connected && this.client?.active) return;

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
      this.onConnectHandlers.forEach((handler) => handler());

      // Process pending subscriptions
      this.pendingSubscriptions.forEach(({ gameId, handler }) => {
        this.subscribeToGame(gameId, handler);
      });
      this.pendingSubscriptions = [];
    };

    this.client.onDisconnect = () => {
      this.connected = false;
      console.log('[WS] Disconnected');
      this.onDisconnectHandlers.forEach((handler) => handler());
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
      this.pendingSubscriptions = [];
    }
  }

  onConnect(handler: ConnectionHandler): () => void {
    this.onConnectHandlers.add(handler);
    // If already connected, call handler immediately
    if (this.connected) {
      handler();
    }
    return () => {
      this.onConnectHandlers.delete(handler);
    };
  }

  onDisconnect(handler: ConnectionHandler): () => void {
    this.onDisconnectHandlers.add(handler);
    return () => {
      this.onDisconnectHandlers.delete(handler);
    };
  }

  subscribeToGame(gameId: string, handler: EventHandler): () => void {
    // Queue subscription if not connected yet
    if (!this.client || !this.connected) {
      this.pendingSubscriptions.push({ gameId, handler });
      return () => {
        this.pendingSubscriptions = this.pendingSubscriptions.filter(
          (sub) => !(sub.gameId === gameId && sub.handler === handler)
        );
      };
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

  subscribeToPlayer(playerId: string, handler: EventHandler): () => void {
    if (!this.client || !this.connected) {
      console.warn('[WS] Not connected, cannot subscribe');
      return () => {};
    }

    const topic = `/user/${playerId}/queue/events`;

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

  submitAnswer(gameId: string, questionId: string, selectedOptionIndex: number) {
    if (!this.client || !this.connected) return;
    this.client.publish({
      destination: `/app/game/${gameId}/answer`,
      body: JSON.stringify({ questionId, selectedOptionIndex }),
    });
  }

  isConnected(): boolean {
    return this.connected;
  }
}

export const websocketService = new WebSocketService();

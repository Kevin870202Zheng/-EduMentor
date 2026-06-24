// ================================================================
// EduMentor WebSocket 实时通信客户端
// 连接端点: ws://host:8080/ws/chat?token=<jwt>
// 消息协议: JSON { type, payload, timestamp }
// ================================================================

import { tokenManager } from './apiClient';

/** WebSocket 消息类型（客户端 → 服务端） */
export type ClientMessageType =
  | 'CHAT_SEND'
  | 'STUDY_SESSION_START'
  | 'STUDY_SESSION_PROGRESS'
  | 'STUDY_SESSION_END'
  | 'STUDY_SESSION_INTERRUPT'
  | 'HEARTBEAT'
  | 'GET_HISTORY'
  | 'GET_ONLINE';

/** WebSocket 消息类型（服务端 → 客户端） */
export type ServerMessageType =
  | 'CHAT_MESSAGE'
  | 'CHAT_HISTORY'
  | 'STUDY_SESSION'
  | 'HEARTBEAT_ACK'
  | 'ONLINE_COUNT'
  | 'ERROR';

/** 客户端消息结构 */
export interface WsClientMessage {
  type: ClientMessageType;
  payload: Record<string, unknown>;
  timestamp: string;
}

/** 服务端消息结构 */
export interface WsServerMessage {
  type: ServerMessageType;
  payload: Record<string, unknown>;
  timestamp: string;
}

/** 消息回调 */
export type WsMessageHandler = (msg: WsServerMessage) => void;
export type WsStatusHandler = (connected: boolean) => void;

/** 默认 WebSocket URL */
const DEFAULT_WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/chat';

/**
 * WebSocket 客户端
 *
 * 特性：
 * - JWT Token 握手认证
 * - 自动重连（指数退避）
 * - 心跳保活
 * - 消息回调分发
 */
class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000; // 初始 1s，指数退避
  private heartbeatInterval = 30000; // 30s
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private messageHandlers: Set<WsMessageHandler> = new Set();
  private statusHandlers: Set<WsStatusHandler> = new Set();
  private destroyed = false;

  constructor(url: string = DEFAULT_WS_URL) {
    this.url = url;
  }

  /** 当前连接状态 */
  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /** 建立连接 */
  connect(): void {
    if (this.isConnected || this.destroyed) return;

    const token = tokenManager.getAccessToken();
    if (!token) {
      console.warn('[WS] 无 Token，无法建立 WebSocket 连接');
      this.notifyStatus(false);
      return;
    }

    try {
      this.ws = new WebSocket(`${this.url}?token=${token}`);
    } catch (err) {
      console.error('[WS] 创建连接失败:', err);
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      console.info('[WS] 连接已建立');
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;
      this.notifyStatus(true);
      this.startHeartbeat();
    };

    this.ws.onmessage = (event: MessageEvent) => {
      try {
        const msg: WsServerMessage = JSON.parse(event.data as string);
        this.messageHandlers.forEach((handler) => handler(msg));
      } catch (err) {
        console.warn('[WS] 消息解析失败:', err);
      }
    };

    this.ws.onclose = (event: CloseEvent) => {
      console.info(`[WS] 连接已关闭 (code=${event.code})`);
      this.stopHeartbeat();
      this.notifyStatus(false);
      if (!this.destroyed) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (event: Event) => {
      console.error('[WS] 连接错误:', event);
    };
  }

  /** 断开连接 */
  disconnect(): void {
    this.destroyed = true;
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.onclose = null; // 防止触发重连
      this.ws.close(1000, '客户端主动断开');
      this.ws = null;
    }
    this.notifyStatus(false);
  }

  /** 发送消息 */
  send(type: ClientMessageType, payload: Record<string, unknown> = {}): void {
    if (!this.isConnected) {
      console.warn('[WS] 未连接，无法发送消息');
      return;
    }

    const msg: WsClientMessage = {
      type,
      payload,
      timestamp: new Date().toISOString(),
    };

    this.ws?.send(JSON.stringify(msg));
  }

  /** 发送聊天消息（便捷方法） */
  sendChat(
    sessionId: string,
    content: string,
    options?: {
      messageType?: string;
      relatedKpId?: string;
      llmModel?: string;
    },
  ): void {
    this.send('CHAT_SEND', {
      sessionId,
      content,
      ...(options?.messageType && { messageType: options.messageType }),
      ...(options?.relatedKpId && { relatedKpId: options.relatedKpId }),
      ...(options?.llmModel && { llmModel: options.llmModel }),
    });
  }

  /** 开始学习会话（便捷方法） */
  startStudySession(learningPathNodeId?: string, focusScore?: number): void {
    this.send('STUDY_SESSION_START', {
      ...(learningPathNodeId && { learningPathNodeId }),
      ...(focusScore !== undefined && { focusScore }),
    });
  }

  /** 更新学习会话进度（便捷方法） */
  updateStudyProgress(
    sessionId: string,
    data: { questionsAnswered?: number; correctCount?: number; focusScore?: number },
  ): void {
    this.send('STUDY_SESSION_PROGRESS', { sessionId, ...data });
  }

  /** 结束学习会话（便捷方法） */
  endStudySession(sessionId: string): void {
    this.send('STUDY_SESSION_END', { sessionId });
  }

  /** 发送心跳 */
  sendHeartbeat(): void {
    this.send('HEARTBEAT');
  }

  /** 注册消息处理器 */
  onMessage(handler: WsMessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  /** 注册连接状态处理器 */
  onStatus(handler: WsStatusHandler): () => void {
    this.statusHandlers.add(handler);
    return () => this.statusHandlers.delete(handler);
  }

  // ── 私有方法 ──

  private scheduleReconnect(): void {
    if (this.destroyed || this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[WS] 已达到最大重连次数，停止重连');
      return;
    }

    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts);
    const jitter = Math.random() * 1000;
    const totalDelay = Math.min(delay + jitter, 30000); // 最大 30s

    console.info(`[WS] ${totalDelay}ms 后尝试第 ${this.reconnectAttempts + 1} 次重连`);
    setTimeout(() => {
      if (!this.destroyed) {
        this.reconnectAttempts++;
        this.connect();
      }
    }, totalDelay);
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat();
    }, this.heartbeatInterval);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private notifyStatus(connected: boolean): void {
    this.statusHandlers.forEach((handler) => handler(connected));
  }
}

/** 全局单例 WebSocket 客户端 */
export const wsClient = new WebSocketClient();

export default WebSocketClient;

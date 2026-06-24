// ================================================================
// 智能答疑模块 API
// /api/qa/ask, /qa/ask/stream (SSE), /qa/history, /qa/sessions
// ================================================================

import apiClient from './apiClient';
import type { ChatRequest, ChatResponse, ChatHistoryDto } from './types';

/** SSE 事件回调 */
export interface SseCallbacks {
  onChunk?: (content: string) => void;
  onDone?: (sessionId: string, totalTokens: number) => void;
  onError?: (message: string) => void;
}

export const qaApi = {
  /**
   * 同步问答
   * POST /api/qa/ask
   */
  async ask(request: ChatRequest): Promise<ChatResponse> {
    return apiClient.post<unknown, ChatResponse>('/qa/ask', request);
  },

  /**
   * SSE 流式问答
   * GET /api/qa/ask/stream
   *
   * 使用 EventSource 或 fetch + ReadableStream 接收流式响应
   */
  streamAsk(
    request: ChatRequest,
    callbacks: SseCallbacks,
  ): AbortController {
    const controller = new AbortController();

    const params = new URLSearchParams();
    params.set('content', request.content);
    params.set('stream', 'true');
    if (request.sessionId) params.set('sessionId', request.sessionId);
    if (request.messageType) params.set('messageType', request.messageType);
    if (request.relatedKpId) params.set('relatedKpId', request.relatedKpId);

    const baseUrl = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';
    const token = localStorage.getItem('edumentor_access_token');

    fetch(`${baseUrl}/qa/ask/stream?${params.toString()}`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          callbacks.onError?.('SSE 连接失败');
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          callbacks.onError?.('无法读取响应流');
          return;
        }

        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith('event: ')) {
              const eventType = line.slice(7).trim();
              // 下一行是 data:
              continue;
            }
            if (line.startsWith('data: ')) {
              const data = line.slice(6).trim();
              try {
                const parsed = JSON.parse(data);
                if (parsed.content) {
                  callbacks.onChunk?.(parsed.content);
                }
                if (parsed.sessionId && parsed.totalTokens !== undefined) {
                  callbacks.onDone?.(parsed.sessionId, parsed.totalTokens);
                }
                if (parsed.message) {
                  callbacks.onError?.(parsed.message);
                }
              } catch {
                // 非 JSON 数据，作为纯文本 chunk
                if (data) callbacks.onChunk?.(data);
              }
            }
          }
        }
      })
      .catch((err) => {
        if (err.name !== 'AbortError') {
          callbacks.onError?.(err.message || 'SSE 流式请求失败');
        }
      });

    return controller;
  },

  /**
   * 获取对话历史
   * GET /api/qa/history?sessionId={sessionId}
   */
  async getHistory(sessionId: string): Promise<ChatHistoryDto[]> {
    return apiClient.get<unknown, ChatHistoryDto[]>('/qa/history', {
      params: { sessionId },
    });
  },

  /**
   * 获取会话列表
   * GET /api/qa/sessions
   */
  async getSessions(): Promise<string[]> {
    return apiClient.get<unknown, string[]>('/qa/sessions');
  },

  /**
   * 删除会话
   * DELETE /api/qa/sessions/{sessionId}
   */
  async deleteSession(sessionId: string): Promise<void> {
    return apiClient.delete<unknown, void>(`/qa/sessions/${sessionId}`);
  },

  /**
   * 获取最近消息
   * GET /api/qa/recent?limit={limit}
   */
  async getRecentMessages(limit: number = 5): Promise<ChatHistoryDto[]> {
    return apiClient.get<unknown, ChatHistoryDto[]>('/qa/recent', {
      params: { limit: Math.min(limit, 50) },
    });
  },
};

// ================================================================
// EduMentor API Client — Axios 实例 + JWT 拦截器
// 适配 Spring Boot 后端 ApiResponse<T> 统一响应格式
// ================================================================

import axios, {
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { ApiError, ApiFieldError, ApiResponse } from './types';

// ─── 环境配置 ──────────────────────────────────────────────

/**
 * API 基地址
 * - 开发环境: VITE_API_BASE 环境变量或默认 http://localhost:8080/api
 * - 生产环境: 同域部署时使用 "/api"
 */
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';

/** localStorage Key */
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'edumentor_access_token',
  REFRESH_TOKEN: 'edumentor_refresh_token',
  USER: 'edumentor_user',
} as const;

// ─── Token 管理 ─────────────────────────────────────────────

export const tokenManager = {
  /** 获取 Access Token */
  getAccessToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
  },

  /** 获取 Refresh Token */
  getRefreshToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
  },

  /** 保存 Token 对 */
  saveTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
  },

  /** 保存用户信息 */
  saveUser(user: unknown): void {
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
  },

  /** 获取用户信息 */
  getUser<T = unknown>(): T | null {
    const raw = localStorage.getItem(STORAGE_KEYS.USER);
    return raw ? (JSON.parse(raw) as T) : null;
  },

  /** 清除所有认证信息 */
  clearAuth(): void {
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
  },

  /** 是否已登录 */
  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  },
};

// ─── Axios 实例 ─────────────────────────────────────────────

const apiClient = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ─── 请求拦截器: 自动注入 JWT Bearer Token ────────────────

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenManager.getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error),
);

// ─── 响应拦截器: 解包 ApiResponse<T> ──────────────────────

let isRefreshing = false;
let pendingRequests: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
}> = [];

apiClient.interceptors.response.use(
  // ── 成功响应处理 ──
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data;

    // 检查是否 Spring Boot ApiResponse 格式
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        // ✅ 成功 — 直接解包返回 data 字段
        return Promise.resolve(body.data);
      }
      // ❌ 业务错误 — 转换为 ApiError
      return Promise.reject(
        new ApiError(body.code, body.message || '请求失败', body.errors as ApiFieldError[] | undefined),
      );
    }

    // 非标准格式（如直接返回列表、字符串等），透传
    return Promise.resolve(body);
  },

  // ── HTTP 错误处理 ──
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // 401 未授权 → 尝试刷新 Token
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = tokenManager.getRefreshToken();

      if (!refreshToken) {
        // 没有 Refresh Token → 强制登出
        tokenManager.clearAuth();
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(new ApiError(401, '请重新登录'));
      }

      // 避免并发刷新
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingRequests.push({ resolve, reject });
        }).then((token) => {
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${token}`;
          }
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { accessToken, refreshToken: newRefresh } = await refreshTokens(refreshToken);
        tokenManager.saveTokens(accessToken, newRefresh);

        // 重试所有挂起的请求
        pendingRequests.forEach((p) => p.resolve(accessToken));
        pendingRequests = [];

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        }
        return apiClient(originalRequest);
      } catch {
        // 刷新失败 → 强制登出
        tokenManager.clearAuth();
        pendingRequests.forEach((p) => p.reject(new ApiError(401, 'Token 刷新失败')));
        pendingRequests = [];
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(new ApiError(401, '登录已过期，请重新登录'));
      } finally {
        isRefreshing = false;
      }
    }

    // 403 权限不足
    if (error.response?.status === 403) {
      return Promise.reject(new ApiError(403, '权限不足'));
    }

    // 404 资源不存在
    if (error.response?.status === 404) {
      return Promise.reject(new ApiError(404, '请求的资源不存在'));
    }

    // 502 外部服务异常
    if (error.response?.status === 502) {
      return Promise.reject(new ApiError(502, '外部服务暂时不可用'));
    }

    // 网络错误
    if (!error.response) {
      return Promise.reject(new ApiError(0, '网络连接失败，请检查网络'));
    }

    // 其他 HTTP 错误
    const status = error.response.status;
    const data = error.response.data as ApiResponse<unknown> | undefined;
    return Promise.reject(
      new ApiError(
        data?.code ?? status,
        data?.message || `请求失败 (${status})`,
        data?.errors as ApiFieldError[] | undefined,
      ),
    );
  },
);

// ─── Token 刷新函数 ────────────────────────────────────────

async function refreshTokens(refreshToken: string): Promise<{
  accessToken: string;
  refreshToken: string;
}> {
  const response = await axios.post<ApiResponse<{
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    tokenType: string;
  }>>(`${API_BASE}/auth/refresh`, { refreshToken });

  const data = response.data;
  if (data.code === 200 && data.data) {
    return {
      accessToken: data.data.accessToken,
      refreshToken: data.data.refreshToken,
    };
  }
  throw new ApiError(data.code, data.message || 'Token 刷新失败');
}

// ─── 便捷方法 ─────────────────────────────────────────────

/**
 * 从 Axios 响应中提取分页数据
 * 适用于后端返回 ApiResponse<PaginatedResponse<T>> 的场景
 */
export function extractPaginatedData<T>(data: {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  hasMore: boolean;
}): { items: T[]; total: number; page: number; size: number; totalPages: number; hasMore: boolean } {
  return data;
}

export { apiClient };
export default apiClient;

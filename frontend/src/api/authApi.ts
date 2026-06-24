// ================================================================
// 认证模块 API
// POST /api/auth/login, /register, /refresh, /logout
// ================================================================

import apiClient from './apiClient';
import { tokenManager } from './apiClient';
import type {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  UserDto,
} from './types';

/** 认证 API */
export const authApi = {
  /**
   * 用户登录
   * POST /api/auth/login
   */
  async login(username: string, password: string): Promise<TokenResponse & { user?: UserDto }> {
    const result = await apiClient.post<
      unknown,
      TokenResponse & { user?: UserDto }
    >('/auth/login', { username, password } as LoginRequest);

    if (result.accessToken) {
      tokenManager.saveTokens(result.accessToken, result.refreshToken);
      if (result.user) {
        tokenManager.saveUser(result.user);
      }
    }
    return result;
  },

  /**
   * 用户注册
   * POST /api/auth/register
   */
  async register(request: RegisterRequest): Promise<TokenResponse> {
    const result = await apiClient.post<unknown, TokenResponse>(
      '/auth/register',
      request,
    );
    if (result.accessToken) {
      tokenManager.saveTokens(result.accessToken, result.refreshToken);
    }
    return result;
  },

  /**
   * 刷新 Token
   * POST /api/auth/refresh
   */
  async refreshToken(refreshToken: string): Promise<TokenResponse> {
    const result = await apiClient.post<unknown, TokenResponse>(
      '/auth/refresh',
      { refreshToken },
    );
    return result;
  },

  /**
   * 登出
   * POST /api/auth/logout
   */
  async logout(): Promise<void> {
    try {
      await apiClient.post('/auth/logout');
    } catch {
      // best-effort
    }
    tokenManager.clearAuth();
  },
};

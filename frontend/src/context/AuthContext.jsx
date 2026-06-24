import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authAPI } from '../services/api';

const AuthContext = createContext(null);

const TOKEN_KEY = 'edumentor_access_token';
const REFRESH_KEY = 'edumentor_refresh_token';
const USER_KEY = 'edumentor_user';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true); // 初始化加载

  // 从 localStorage 恢复登录状态
  useEffect(() => {
    try {
      const savedToken = localStorage.getItem(TOKEN_KEY);
      const savedUser = localStorage.getItem(USER_KEY);
      if (savedToken && savedUser) {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
      }
    } catch (e) {
      // 忽略解析错误
      clearStorage();
    }
    setLoading(false);
  }, []);

  const clearStorage = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  };

  const saveSession = (token, refreshToken, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setToken(token);
    setUser(user);
  };

  const login = useCallback(async (username, password) => {
    const res = await authAPI.login({ username, password });
    // 后端返回 { code, success, data: { accessToken, refreshToken, ... } }
    // api 拦截器已解包 response.data
    const payload = res.data || res;
    const token = payload.accessToken;
    const refreshToken = payload.refreshToken;

    // 从 JWT payload 中解析用户信息（base64 解码）
    let user = null;
    if (token) {
      try {
        const encoded = token.split('.')[1];
        const decoded = JSON.parse(atob(encoded));
        user = {
          id: decoded.sub,
          username,
          role: (decoded.role || '').toLowerCase(),
        };
      } catch {
        // 解析失败，使用默认值
        user = { username, role: 'student' };
      }
    }

    saveSession(token, refreshToken, user);
    return user;
  }, []);

  const register = useCallback(async (data) => {
    const res = await authAPI.register(data);
    const payload = res.data || res;
    const token = payload.accessToken;
    const refreshToken = payload.refreshToken;

    let user = null;
    if (token) {
      try {
        const encoded = token.split('.')[1];
        const decoded = JSON.parse(atob(encoded));
        user = {
          id: decoded.sub,
          username: data.username,
          role: (decoded.role || '').toLowerCase(),
        };
      } catch {
        user = { username: data.username, role: 'student' };
      }
    }

    saveSession(token, refreshToken, user);
    return user;
  }, []);

  const logout = useCallback(() => {
    clearStorage();
    setToken(null);
    setUser(null);
  }, []);

  const isAuthenticated = !!token && !!user;
  const isTeacher = user?.role === 'teacher';
  const isStudent = user?.role === 'student';

  return (
    <AuthContext.Provider value={{
      user, token, loading,
      isAuthenticated, isTeacher, isStudent,
      login, register, logout,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

export default AuthContext;

import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from '../../context/AuthContext';

/**
 * 路由守卫组件
 * - 未登录 -> 重定向到 /login（保留原始地址）
 * - 角色不匹配 -> 重定向到对应角色的首页
 * - 登录且角色匹配 -> 渲染子页面
 */
export default function ProtectedRoute({ children, allowedRoles }) {
  const { isAuthenticated, user, loading } = useAuth();
  const location = useLocation();

  // 初始化加载中
  if (loading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        flexDirection: 'column',
        gap: 16,
      }}>
        <Spin size="large" />
        <span style={{ color: '#999' }}>加载中...</span>
      </div>
    );
  }

  // 未登录 -> 跳转到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // 指定了角色白名单但用户角色不匹配
  if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(user?.role)) {
    // 根据用户角色跳转到对应的首页
    const homePath = user?.role === 'teacher' ? '/teacher/dashboard'
      : user?.role === 'admin' ? '/admin/dashboard'
      : '/student/dashboard';
    return <Navigate to={homePath} replace />;
  }

  return children;
}

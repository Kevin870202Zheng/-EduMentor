import { useState } from 'react';
import {
  Form, Input, Button, Card, Typography, message, Tabs, Space,
} from 'antd';
import {
  UserOutlined, LockOutlined, BookOutlined, TeamOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

export default function Login() {
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 登录后重定向到之前访问的页面，默认为学生面板
  const from = location.state?.from?.pathname || '/student/dashboard';

  const handleLogin = async (values) => {
    setLoading(true);
    try {
      const user = await login(values.username, values.password);
      message.success(`欢迎回来，${user.real_name || user.username}！`);
      // 根据角色跳转到对应页面
      if (user.role === 'teacher') {
        navigate('/teacher/dashboard');
      } else {
        navigate('/student/dashboard');
      }
    } catch (err) {
      message.error(err.message || '登录失败，请检查用户名和密码');
    }
    setLoading(false);
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: 24,
    }}>
      <Card
        style={{
          width: 420,
          borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.15)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ fontSize: 48, marginBottom: 8 }}>
            <BookOutlined style={{ color: '#1677ff' }} />
          </div>
          <Title level={3} style={{ margin: 0 }}>智学导师 EduMentor</Title>
          <Text type="secondary">学习赋能AI教育智能体</Text>
        </div>

        <Form
          name="login"
          onFinish={handleLogin}
          layout="vertical"
          size="large"
          autoComplete="off"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
              autoFocus
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
              icon={<UserOutlined />}
            >
              登 录
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            <Space>
              <Text type="secondary">还没有账号？</Text>
              <Link to="/register">立即注册</Link>
            </Space>
          </div>
        </Form>

        <div style={{
          marginTop: 24,
          padding: 16,
          background: '#f6f8ff',
          borderRadius: 8,
        }}>
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
            💡 演示账号
          </Text>
          <Space direction="vertical" style={{ width: '100%', fontSize: 13 }}>
            <Text type="secondary">
              <TeamOutlined /> 学生：<Text code>student1</Text> / <Text code>123456</Text>
            </Text>
            <Text type="secondary">
              <UserOutlined /> 教师：<Text code>teacher1</Text> / <Text code>123456</Text>
            </Text>
          </Space>
        </div>
      </Card>
    </div>
  );
}

import { useState } from 'react';
import {
  Form, Input, Button, Card, Typography, message, Select, Space,
} from 'antd';
import {
  UserOutlined, LockOutlined, MailOutlined, PhoneOutlined,
  BookOutlined, IdcardOutlined,
} from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;
const { Option } = Select;

export default function Register() {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleRegister = async (values) => {
    setLoading(true);
    try {
      const user = await register(values);
      message.success(`注册成功！欢迎，${user.real_name || user.username}`);
      if (user.role === 'teacher') {
        navigate('/teacher/dashboard');
      } else {
        navigate('/student/dashboard');
      }
    } catch (err) {
      message.error(err.message || '注册失败，请稍后重试');
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
          width: 480,
          borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.15)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 48, marginBottom: 8 }}>
            <BookOutlined style={{ color: '#1677ff' }} />
          </div>
          <Title level={3} style={{ margin: 0 }}>注册 EduMentor</Title>
          <Text type="secondary">开启个性化学习之旅</Text>
        </div>

        <Form
          form={form}
          name="register"
          onFinish={handleRegister}
          layout="vertical"
          size="large"
          autoComplete="off"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, message: '用户名至少3个字符' },
              { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="3-20位字母数字或下划线" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6位' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="至少6位密码" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="再次输入密码" />
          </Form.Item>

          <Form.Item
            name="role"
            label="角色"
            rules={[{ required: true, message: '请选择角色' }]}
          >
            <Select placeholder="选择你的角色">
              <Option value="student">学生</Option>
              <Option value="teacher">教师</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="real_name"
            label="真实姓名"
          >
            <Input prefix={<IdcardOutlined />} placeholder="选填，用于显示在系统中" />
          </Form.Item>

          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
          >
            <Input prefix={<MailOutlined />} placeholder="选填，用于找回密码" />
          </Form.Item>

          <Form.Item
            name="phone"
            label="手机号"
          >
            <Input prefix={<PhoneOutlined />} placeholder="选填" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
              icon={<UserOutlined />}
            >
              注 册
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            <Space>
              <Text type="secondary">已有账号？</Text>
              <Link to="/login">立即登录</Link>
            </Space>
          </div>
        </Form>
      </Card>
    </div>
  );
}

import { useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Typography, message, Modal } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  NodeIndexOutlined,
  QuestionCircleOutlined,
  FileExclamationOutlined,
  UserOutlined,
  BookOutlined,
  LogoutOutlined,
  SwapOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../context/AuthContext';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const studentMenuItems = [
  { key: '/student/dashboard', icon: <DashboardOutlined />, label: '学情总览' },
  { key: '/student/learning-path', icon: <NodeIndexOutlined />, label: '学习路径' },
  { key: '/student/qa', icon: <QuestionCircleOutlined />, label: '智能答疑' },
  { key: '/student/error-review', icon: <FileExclamationOutlined />, label: '错题复盘' },
];

const teacherMenuItems = [
  { key: '/teacher/dashboard', icon: <DashboardOutlined />, label: '教学驾驶舱' },
];

export default function MainLayout({ role = 'student' }) {
  const [collapsed, setCollapsed] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, isAuthenticated } = useAuth();

  const menuItems = role === 'teacher' ? teacherMenuItems : studentMenuItems;
  const title = role === 'teacher' ? 'EduMentor 教学端' : '智学导师 EduMentor';
  const displayName = user?.real_name || user?.username || (role === 'teacher' ? '张老师' : '同学');

  const handleLogout = () => {
    Modal.confirm({
      title: '确认退出',
      content: '确定要退出登录吗？',
      okText: '退出',
      cancelText: '取消',
      onOk: () => {
        logout();
        message.success('已退出登录');
        navigate('/login');
      },
    });
  };

  const handleSwitchRole = () => {
    setRoleModalOpen(false);
    // 切换角色 = 登出后跳转到登录页
    logout();
    navigate('/login');
  };

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: (
        <span>
          {displayName}
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            ({role === 'teacher' ? '教师' : '学生'})
          </Text>
        </span>
      ),
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'switch-role',
      icon: <SwapOutlined />,
      label: '切换角色',
      onClick: () => setRoleModalOpen(true),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        style={{ background: '#fff' }}
        theme="light"
      >
        <div style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderBottom: '1px solid #f0f0f0',
          cursor: 'pointer',
        }}
        onClick={() => navigate(role === 'teacher' ? '/teacher/dashboard' : '/student/dashboard')}
        >
          <BookOutlined style={{ fontSize: 24, color: '#1677ff' }} />
          {!collapsed && <Text strong style={{ marginLeft: 8, fontSize: 16 }}>{title}</Text>}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Layout>
        <Header style={{
          background: '#fff',
          padding: '0 24px',
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          borderBottom: '1px solid #f0f0f0',
        }}>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar
                icon={<UserOutlined />}
                style={{ backgroundColor: role === 'teacher' ? '#722ed1' : '#1677ff' }}
              />
              <Text>{displayName}</Text>
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: 16, padding: 24, background: '#f5f5f5', minHeight: 280, borderRadius: 8 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

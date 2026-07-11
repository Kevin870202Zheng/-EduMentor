import { useState, useEffect, useCallback } from 'react';
import { Layout, Menu, Avatar, Dropdown, Typography, message, Modal, Select, Space } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined, NodeIndexOutlined, QuestionCircleOutlined,
  FileExclamationOutlined, UserOutlined, BookOutlined,
  LogoutOutlined, SwapOutlined, FileTextOutlined, ReadOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../context/AuthContext';
import { enrollmentAPI } from '../../services/api';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const studentMenuItems = [
  { key: '/student/dashboard', icon: <DashboardOutlined />, label: '学情总览' },
  { key: '/student/learning', icon: <ReadOutlined />, label: '课程学习' },
  { key: '/student/learning-path', icon: <NodeIndexOutlined />, label: '学习路径' },
  { key: '/student/qa', icon: <QuestionCircleOutlined />, label: '智能答疑' },
  { key: '/student/error-review', icon: <FileExclamationOutlined />, label: '错题复盘' },
  { key: '/student/courses', icon: <BookOutlined />, label: '我的课程' },
  { key: '/student/profile', icon: <UserOutlined />, label: '个人信息' },
];

const teacherMenuItems = [
  { key: '/teacher/dashboard', icon: <DashboardOutlined />, label: '教学驾驶舱' },
  { key: '/teacher/courses', icon: <FileTextOutlined />, label: '课程管理' },
];

export default function MainLayout({ role = 'student' }) {
  const [collapsed, setCollapsed] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [studentCourses, setStudentCourses] = useState([]);
  const [selectedCourseId, setSelectedCourseId] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();

  const menuItems = role === 'teacher' ? teacherMenuItems : studentMenuItems;
  const title = role === 'teacher' ? 'EduMentor 教学端' : '智学导师 EduMentor';
  const displayName = user?.real_name || user?.username || (role === 'teacher' ? '张老师' : '同学');

  // Load student courses and set current course
  useEffect(() => {
    if (role === 'student' && user?.id) {
      loadCourses();
    }
  }, [user?.id]);

  // 🔗 联动：加载选课列表并同步 selectedCourseId
  const loadCourses = useCallback(async () => {
    if (!user?.id) return;
    try {
      const res = await enrollmentAPI.listByStudent(user.id);
      const list = res?.data || res || [];
      setStudentCourses(list);
      // Restore saved course or use first one
      const saved = localStorage.getItem('currentCourseId');
      if (saved && list.some(c => c.courseId === saved)) {
        setSelectedCourseId(saved);
      } else if (list.length > 0) {
        setSelectedCourseId(list[0].courseId);
      } else {
        setSelectedCourseId(null);
        localStorage.removeItem('currentCourseId');
      }
    } catch (e) {
      setStudentCourses([]);
    }
  }, [user?.id]);

  // 🔗 联动：退课后刷新课程列表并同步上下文
  const refreshCourses = useCallback(async () => {
    await loadCourses();
  }, [loadCourses]);

  // Save selected course to localStorage
  useEffect(() => {
    if (selectedCourseId) {
      localStorage.setItem('currentCourseId', selectedCourseId);
    }
  }, [selectedCourseId]);

  const handleLogout = () => {
    Modal.confirm({
      title: '确认退出', content: '确定要退出登录吗？', okText: '退出', cancelText: '取消',
      onOk: () => { logout(); message.success('已退出登录'); navigate('/login'); },
    });
  };

  const handleSwitchRole = () => {
    setRoleModalOpen(false);
    logout();
    navigate('/login');
  };

  const userMenuItems = [
    { key: 'profile', icon: <UserOutlined />, label: <span>{displayName}<Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>({role === 'teacher' ? '教师' : '学生'})</Text></span>, disabled: true },
    { type: 'divider' },
    { key: 'switch-role', icon: <SwapOutlined />, label: '切换角色', onClick: () => setRoleModalOpen(true) },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true, onClick: handleLogout },
  ];

  const currentCourse = studentCourses.find(c => c.courseId === selectedCourseId);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} style={{ background: '#fff' }} theme="light">
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', borderBottom: '1px solid #f0f0f0', cursor: 'pointer' }}
          onClick={() => navigate(role === 'teacher' ? '/teacher/dashboard' : '/student/dashboard')}>
          <BookOutlined style={{ fontSize: 24, color: '#1677ff' }} />
          {!collapsed && <Text strong style={{ marginLeft: 8, fontSize: 16 }}>{title}</Text>}
        </div>

        {/* Student course switcher */}
        {role === 'student' && !collapsed && studentCourses.length > 0 && (
          <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0' }}>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>当前课程</Text>
            <Select
              style={{ width: '100%' }}
              size="small"
              value={selectedCourseId}
              onChange={setSelectedCourseId}
              options={studentCourses.map(c => ({ label: c.courseName || c.courseCode, value: c.courseId }))}
            />
          </div>
        )}

        <Menu mode="inline" selectedKeys={[location.pathname.startsWith('/student/learning') ? '/student/learning' : location.pathname]} items={menuItems}
          onClick={({ key }) => {
            if (key === '/student/learning') {
              const code = currentCourse?.courseCode;
              if (code) navigate(`/student/learning/${code}`);
              else message.warning('请先选择一门课程');
            } else if (key === '/teacher/courses') {
              navigate(key);
            } else {
              navigate(key);
            }
          }}
          style={{ borderRight: 0 }} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', borderBottom: '1px solid #f0f0f0' }}>
          <Space>
            {role === 'student' && currentCourse && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                当前课程: <Text strong>{currentCourse.courseName}</Text>
              </Text>
            )}
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar icon={<UserOutlined />} style={{ backgroundColor: role === 'teacher' ? '#722ed1' : '#1677ff' }} />
                <Text>{displayName}</Text>
              </div>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16, padding: 24, background: '#f5f5f5', minHeight: 280, borderRadius: 8 }}>
          <Outlet context={{ selectedCourseId, studentCourses, setSelectedCourseId, refreshCourses }} />
        </Content>
      </Layout>
    </Layout>
  );
}

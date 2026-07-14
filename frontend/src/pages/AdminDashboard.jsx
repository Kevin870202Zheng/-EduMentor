import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Spin, Typography } from 'antd';
import { UserOutlined, TeamOutlined, BookOutlined, CheckCircleOutlined, StopOutlined } from '@ant-design/icons';
import { adminAPI } from '../services/api';

const { Text } = Typography;

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadStats(); }, []);

  const loadStats = async () => {
    try {
      const res = await adminAPI.getStats();
      setStats(res?.data || res);
    } catch (err) {
      console.error('加载统计数据失败', err);
    }
    setLoading(false);
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 120 }} />;

  return (
    <div>
      <Card title="📊 系统概览" style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col span={6}>
            <Card><Statistic title="总用户数" value={stats?.totalUsers || 0} prefix={<UserOutlined />} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="教师数" value={stats?.teacherCount || 0} prefix={<TeamOutlined />}
              valueStyle={{ color: '#1677ff' }} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="学生数" value={stats?.studentCount || 0} prefix={<TeamOutlined />}
              valueStyle={{ color: '#52c41a' }} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="课程数" value={stats?.courseCount || 0} prefix={<BookOutlined />}
              valueStyle={{ color: '#722ed1' }} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="活跃教师" value={stats?.activeTeachers || 0} prefix={<CheckCircleOutlined />}
              suffix={<Text type="secondary">/ {stats?.teacherCount || 0}</Text>} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="活跃学生" value={stats?.activeStudents || 0} prefix={<CheckCircleOutlined />}
              suffix={<Text type="secondary">/ {stats?.studentCount || 0}</Text>} /></Card>
          </Col>
          <Col span={6}>
            <Card><Statistic title="非活跃教师" value={(stats?.teacherCount || 0) - (stats?.activeTeachers || 0)}
              prefix={<StopOutlined />}
              valueStyle={{ color: (stats?.teacherCount || 0) - (stats?.activeTeachers || 0) > 0 ? '#faad14' : undefined }} /></Card>
          </Col>
        </Row>
      </Card>
    </div>
  );
}

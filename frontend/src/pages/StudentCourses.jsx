import { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, List, Tag, Typography, Spin, message, Empty, Space, Select } from 'antd';
import { PlusOutlined, CheckCircleOutlined, BookOutlined, TeamOutlined } from '@ant-design/icons';
import { enrollmentAPI, courseAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

export default function StudentCourses() {
  const [enrolled, setEnrolled] = useState([]);
  const [availableCourses, setAvailableCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [enrollModalOpen, setEnrollModalOpen] = useState(false);
  const [enrolling, setEnrolling] = useState(null);
  const { user } = useAuth();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const enrolledRes = await enrollmentAPI.listByStudent(user?.id);
      setEnrolled(enrolledRes?.data || enrolledRes || []);
    } catch (e) {
      setEnrolled([]);
    }
    try {
      const allRes = await courseAPI.list({ publishedOnly: true });
      const allCourses = allRes?.data?.content || allRes?.data || [];
      const enrolledIds = new Set(enrolled.map(e => e.courseId));
      setAvailableCourses(allCourses.filter(c => !enrolledIds.has(c.id)));
    } catch (e) {
      setAvailableCourses([]);
    }
    setLoading(false);
  };

  const handleEnroll = async (courseId) => {
    setEnrolling(courseId);
    try {
      await enrollmentAPI.enroll({ studentId: user?.id, courseId });
      message.success('选课成功');
      setEnrollModalOpen(false);
      loadData();
    } catch (err) {
      message.error(err?.message || '选课失败');
    }
    setEnrolling(null);
  };

  const columns = [
    {
      title: '课程编号', dataIndex: 'courseCode', key: 'courseCode', width: 120,
      render: (code) => <Tag color="blue">{code}</Tag>,
    },
    { title: '课程名称', dataIndex: 'courseName', key: 'courseName' },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s) => s === 'active' ? <Tag color="green">学习中</Tag> : <Tag>{s}</Tag>,
    },
    {
      title: '选课时间', dataIndex: 'enrolledAt', key: 'enrolledAt', width: 160,
      render: (t) => t ? new Date(t).toLocaleDateString('zh-CN') : '-',
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📚 我的课程</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { loadData(); setEnrollModalOpen(true); }}>
          选课
        </Button>
      </div>

      <Card>
        {enrolled.length === 0 ? (
          <Empty description="还没有选课，点击右上角开始选课" />
        ) : (
          <Table dataSource={enrolled} columns={columns} rowKey="id" pagination={false} size="middle" />
        )}
      </Card>

      {/* 选课 Modal */}
      <Modal
        title="选课中心"
        open={enrollModalOpen}
        onCancel={() => setEnrollModalOpen(false)}
        footer={null}
        width={600}
      >
        {availableCourses.length === 0 ? (
          <Empty description="没有更多可选的课程" />
        ) : (
          <List
            dataSource={availableCourses}
            renderItem={(course) => (
              <List.Item
                actions={[
                  <Button
                    type="primary"
                    size="small"
                    loading={enrolling === course.id}
                    onClick={() => handleEnroll(course.id)}
                  >
                    选课
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={<BookOutlined style={{ fontSize: 24, color: '#1677ff' }} />}
                  title={<Space><Tag color="blue">{course.courseCode}</Tag>{course.name}</Space>}
                  description={
                    <Text type="secondary">
                      {course.subject} · {course.gradeLevel || '通用'}
                      {course.description && ` · ${course.description.substring(0, 60)}${course.description.length > 60 ? '...' : ''}`}
                    </Text>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Modal>
    </div>
  );
}

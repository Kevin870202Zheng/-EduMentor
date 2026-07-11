import { useNavigate, useOutletContext } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, List, Tag, Typography, Spin, message, Empty, Space, Popconfirm, Tabs } from 'antd';
import { PlusOutlined, BookOutlined, DeleteOutlined, RollbackOutlined } from '@ant-design/icons';
import { enrollmentAPI, courseAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

export default function StudentCourses() {
  const [enrolled, setEnrolled] = useState([]);
  const [dropped, setDropped] = useState([]);
  const [availableCourses, setAvailableCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [enrollModalOpen, setEnrollModalOpen] = useState(false);
  const [enrolling, setEnrolling] = useState(null);
  const [dropping, setDropping] = useState(null);
  const navigate = useNavigate();
  const { user } = useAuth();
  const { setSelectedCourseId, refreshCourses } = useOutletContext();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const enrolledRes = await enrollmentAPI.listByStudent(user?.id);
      const enrolledList = enrolledRes?.data || enrolledRes || [];
      setEnrolled(enrolledList);

      // 加载已退课程列表
      try {
        const droppedRes = await enrollmentAPI.listDropped(user?.id);
        setDropped(droppedRes?.data || droppedRes || []);
      } catch (e) {
        setDropped([]);
      }

      // 获取所有课程和已选课程的差集
      try {
        const allRes = await courseAPI.list({ publishedOnly: true });
        const allCourses = allRes?.data?.items || allRes?.data || [];
        const enrolledIds = new Set(enrolledList.map(e => e.courseId));
        setAvailableCourses(allCourses.filter(c => !enrolledIds.has(c.id)));
      } catch (e) {
        setAvailableCourses([]);
      }
    } catch (e) {
      setEnrolled([]);
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
      // 🔗 联动：通知 MainLayout 刷新课程列表
      if (refreshCourses) await refreshCourses();
    } catch (err) {
      message.error(err?.message || '选课失败');
    }
    setEnrolling(null);
  };

  const handleDrop = async (record) => {
    setDropping(record.id);
    try {
      await enrollmentAPI.drop(record.id);
      message.success(`已退课：${record.courseName || record.courseCode}`);
      loadData();
      // 🔗 联动：通知 MainLayout 刷新课程列表并同步上下文
      if (refreshCourses) await refreshCourses();
    } catch (err) {
      message.error('退课失败');
    }
    setDropping(null);
  };

  // 已退课程：重新选课
  const handleReEnroll = async (record) => {
    setEnrolling(record.courseId);
    try {
      await enrollmentAPI.enroll({ studentId: user?.id, courseId: record.courseId });
      message.success(`已重新选课：${record.courseName || record.courseCode}`);
      loadData();
      if (refreshCourses) await refreshCourses();
    } catch (err) {
      message.error(err?.message || '重新选课失败');
    }
    setEnrolling(null);
  };

  const activeColumns = [
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
    {
      title: '操作', key: 'action', width: 200,
      render: (_, record) => (
        <Space>
          <Button type="primary" size="small" icon={<BookOutlined />}
            onClick={() => {
              if (setSelectedCourseId) setSelectedCourseId(record.courseId);
              navigate(`/student/learning/${record.courseCode}`);
            }}>
            开始学习
          </Button>
          <Popconfirm
            title="确认退课"
            description={`确定要退选「${record.courseName || record.courseCode}」吗？学习进度将被保留。`}
            onConfirm={() => handleDrop(record)}
            okText="确认退课"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />} loading={dropping === record.id}>
              退课
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const droppedColumns = [
    {
      title: '课程编号', dataIndex: 'courseCode', key: 'courseCode', width: 120,
      render: (code) => <Tag color="default">{code}</Tag>,
    },
    { title: '课程名称', dataIndex: 'courseName', key: 'courseName' },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: () => <Tag color="default">已退课</Tag>,
    },
    {
      title: '退课时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160,
      render: (t) => t ? new Date(t).toLocaleDateString('zh-CN') : '-',
    },
    {
      title: '操作', key: 'action', width: 120,
      render: (_, record) => (
        <Button type="primary" size="small" icon={<RollbackOutlined />}
          loading={enrolling === record.courseId}
          onClick={() => handleReEnroll(record)}>
          重新选课
        </Button>
      ),
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  const tabItems = [
    {
      key: 'active',
      label: `我的课程 (${enrolled.length})`,
      children: (
        <Card>
          {enrolled.length === 0 ? (
            <Empty description="还没有选课，点击右上角开始选课" />
          ) : (
            <Table dataSource={enrolled} columns={activeColumns} rowKey="id" pagination={false} size="middle" />
          )}
        </Card>
      ),
    },
    {
      key: 'dropped',
      label: `已退课程 (${dropped.length})`,
      children: (
        <Card>
          {dropped.length === 0 ? (
            <Empty description="暂无已退课程记录" />
          ) : (
            <Table dataSource={dropped} columns={droppedColumns} rowKey="id" pagination={false} size="middle" />
          )}
        </Card>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📚 我的课程</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { loadData(); setEnrollModalOpen(true); }}>
          选课
        </Button>
      </div>

      <Tabs items={tabItems} />

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

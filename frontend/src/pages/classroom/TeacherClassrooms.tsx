import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Card,
  Table,
  Typography,
  Tag,
  Button,
  Spin,
  Empty,
  Space,
  Select,
  Statistic,
  Row,
  Col,
} from 'antd';
import {
  PlayCircleOutlined,
  CheckCircleOutlined,
  BarChartOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { courseAPI } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { classroomApi } from '../../api/classroomApi';

const { Title, Text } = Typography;

/**
 * 教师课堂学情页面
 * 展示课程下所有课堂的完成度、场景级统计分析
 */
const TeacherClassrooms: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const courseIdFromUrl = searchParams.get('courseId');
  const { user } = useAuth();

  const [loading, setLoading] = useState(true);
  const [courses, setCourses] = useState<any[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(courseIdFromUrl);
  const [classrooms, setClassrooms] = useState<any[]>([]);

  useEffect(() => {
    loadCourses();
  }, []);

  useEffect(() => {
    if (selectedCourseId) {
      loadClassrooms(selectedCourseId);
    } else {
      setClassrooms([]);
      setLoading(false);
    }
  }, [selectedCourseId]);

  const loadCourses = async () => {
    try {
      const res = await courseAPI.listByTeacher(user?.id);
      const list = res?.data || res || [];
      setCourses(list);
      if (courseIdFromUrl && list.some((c: any) => c.id === courseIdFromUrl)) {
        setSelectedCourseId(courseIdFromUrl);
      } else if (!selectedCourseId && list.length > 0) {
        setSelectedCourseId(list[0].id);
      } else if (!selectedCourseId) {
        setLoading(false);
      }
    } catch (err) {
      console.error('Failed to load courses:', err);
      setLoading(false);
    }
  };

  const loadClassrooms = async (courseId: string) => {
    try {
      setLoading(true);
      const list = await classroomApi.listClassrooms(courseId);
      setClassrooms(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Failed to load classrooms:', err);
      setClassrooms([]);
    } finally {
      setLoading(false);
    }
  };

  const selectedCourse = courses.find((c: any) => c.id === selectedCourseId);

  const columns = [
    {
      title: '课堂名称',
      dataIndex: 'title',
      key: 'title',
      render: (title: string, record: any) => (
        <Space>
          <span>🎓</span>
          <Text strong>{title}</Text>
          <Tag
            color={
              record.status === 'published'
                ? 'success'
                : record.status === 'generating'
                ? 'processing'
                : 'default'
            }
          >
            {record.status === 'published'
              ? '已发布'
              : record.status === 'generating'
              ? '生成中'
              : '草稿'}
          </Tag>
        </Space>
      ),
    },
    {
      title: '知识点',
      dataIndex: 'knowledgePointName',
      key: 'knowledgePointName',
      render: (v: string) => v || '-',
    },
    {
      title: '场景数',
      dataIndex: 'sceneCount',
      key: 'sceneCount',
      width: 80,
      align: 'center' as const,
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      key: 'difficulty',
      width: 80,
      align: 'center' as const,
      render: (v: number) => (
        <Tag color={v >= 4 ? 'red' : v >= 3 ? 'orange' : 'green'}>
          {'★'.repeat(v || 3)}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (v: string) => (v ? new Date(v).toLocaleDateString('zh-CN') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: any, record: any) => (
        <Button
          type="primary"
          size="small"
          icon={<PlayCircleOutlined />}
          onClick={() => navigate(`/student/classroom/${record.id}`)}
        >
          预览
        </Button>
      ),
    },
  ];

  if (!selectedCourseId) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="暂无课程数据，请先创建课程" />
      </div>
    );
  }

  return (
    <div style={{ padding: '0 4px' }}>
      {/* 顶部标题 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Space>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/teacher/dashboard')}
          />
          <Title level={4} style={{ margin: 0 }}>
            🎓 课堂学情
          </Title>
        </Space>
        <Select
          style={{ width: 280 }}
          placeholder="选择课程"
          value={selectedCourseId}
          onChange={setSelectedCourseId}
          options={courses.map((c: any) => ({
            label: `[${c.courseCode || ''}] ${c.name || c.title}`,
            value: c.id,
          }))}
        />
      </div>

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="课堂总数"
              value={classrooms.length}
              prefix={<BarChartOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="已发布"
              value={classrooms.filter((c) => c.status === 'published').length}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="生成中"
              value={classrooms.filter((c) => c.status === 'generating').length}
              prefix={<Spin style={{ color: '#1677ff' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="总场景数"
              value={classrooms.reduce((s, c) => s + (c.sceneCount || 0), 0)}
              prefix={<BarChartOutlined style={{ color: '#722ed1' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* 课堂列表 */}
      <Card
        title={
          selectedCourse
            ? `📖 ${selectedCourse.name || selectedCourse.title}`
            : '课堂列表'
        }
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
          </div>
        ) : classrooms.length === 0 ? (
          <Empty
            description="暂无课堂数据"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Text type="secondary">
              该课程暂无AI生成的课堂，学生端的"沉浸课堂"将按需自动生成
            </Text>
          </Empty>
        ) : (
          <Table
            dataSource={classrooms}
            columns={columns}
            rowKey={(r: any) => r.id}
            size="small"
            pagination={{
              pageSize: 10,
              showSizeChanger: false,
            }}
          />
        )}
      </Card>
    </div>
  );
};

export default TeacherClassrooms;

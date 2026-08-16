import React, { useEffect, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Button,
  Spin,
  Empty,
  Space,
  message,
  Row,
  Col,
  Alert,
  Progress,
} from 'antd';
import {
  PlayCircleOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import { classroomApi } from '../../api/classroomApi';

const { Title, Text } = Typography;

/**
 * 智慧课堂列表页
 *
 * 展示当前课程已有的 AI 课堂，每个课堂对应一个知识点。
 * 点击「进入学习」进入沉浸式课堂播放器。
 * 如果某个知识点暂无课堂，请在「课程学习」页点击「沉浸课堂」按钮生成。
 */
const ClassroomList: React.FC = () => {
  const navigate = useNavigate();
  const { selectedCourseId, studentCourses } = useOutletContext<{
    selectedCourseId?: string;
    studentCourses: any[];
  }>();

  const [loading, setLoading] = useState(true);
  const [classrooms, setClassrooms] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  const currentCourse = studentCourses?.find(
    (c: any) => c.courseId === selectedCourseId
  );

  useEffect(() => {
    if (selectedCourseId) {
      loadClassrooms();
    } else {
      setLoading(false);
    }
  }, [selectedCourseId]);

  const loadClassrooms = async () => {
    if (!selectedCourseId) return;
    try {
      setLoading(true);
      setError(null);
      const list = await classroomApi.listClassrooms(selectedCourseId);
      setClassrooms(Array.isArray(list) ? list : []);
    } catch (err: any) {
      console.error('加载课堂列表失败:', err);
      setError(err?.message || '加载课堂列表失败');
      setClassrooms([]);
    } finally {
      setLoading(false);
    }
  };

  const handleEnter = (classroomId: string) => {
    navigate(`/student/classroom/${classroomId}`);
  };

  const handleCourt = (classroomId: string) => {
    navigate(`/student/classroom/${classroomId}/court`);
  };

  if (!selectedCourseId) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message="请先选择课程"
          description="在左侧边栏顶部的课程选择器中，选择一门课程后再查看智慧课堂。"
          type="info"
          showIcon
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" tip="加载课堂列表..." />
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
        <div>
          <Title level={4} style={{ margin: 0 }}>
            🎓 智慧课堂
          </Title>
          <Text type="secondary">
            {currentCourse?.courseName || ''}
            {classrooms.length > 0 ? ` · ${classrooms.length} 个课堂` : ''}
          </Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<ExperimentOutlined />}
            onClick={() => navigate('/student/classroom-generator')}
            size="small"
          >
            课堂生成器
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={loadClassrooms}
            size="small"
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 错误提示 */}
      {error && (
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={loadClassrooms}>
              重试
            </Button>
          }
        />
      )}

      {classrooms.length === 0 ? (
        <Card>
          <Empty
            description="暂无课堂"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Space direction="vertical" style={{ textAlign: 'center' }}>
              <Text type="secondary">
                当前课程还没有 AI 课堂，请在「课程学习」页面选择知识点后点击
              </Text>
              <Button
                type="primary"
                onClick={() => navigate('/student/classroom-generator')}
              >
                去课堂生成器创建课堂
              </Button>
            </Space>
          </Empty>
        </Card>
      ) : (
        <Row gutter={[12, 12]}>
          {classrooms.map((cr) => (
            <Col key={cr.id} xs={24} sm={12} md={8} lg={6}>
              <Card
                size="small"
                hoverable
                style={{
                  height: '100%',
                  borderLeft:
                    cr.status === 'published'
                      ? '3px solid #52c41a'
                      : cr.status === 'generating'
                      ? '3px solid #1677ff'
                      : '3px solid #f0f0f0',
                }}
                bodyStyle={{ padding: 12 }}
                actions={[
                  <Button
                    type="primary"
                    block
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => handleEnter(cr.id)}
                    disabled={cr.status !== 'published'}
                  >
                    {cr.status === 'published' ? '进入学习' : '待生成'}
                  </Button>,
                  <Button
                    block
                    size="small"
                    icon={<AuditOutlined />}
                    onClick={() => handleCourt(cr.id)}
                    disabled={cr.status !== 'published'}
                  >
                    模拟法庭
                  </Button>,
                ]}
              >
                {/* 课堂标题 */}
                <Text strong style={{ fontSize: 14 }} ellipsis={{ tooltip: cr.title }}>
                  {cr.title}
                </Text>

                {/* 状态标签 */}
                <div style={{ marginTop: 8, marginBottom: 4 }}>
                  <Space size={4}>
                    <Tag
                      color={
                        cr.status === 'published'
                          ? 'success'
                          : cr.status === 'generating'
                          ? 'processing'
                          : 'default'
                      }
                      style={{ fontSize: 11 }}
                    >
                      {cr.status === 'published'
                        ? '已就绪'
                        : cr.status === 'generating'
                        ? '生成中'
                        : '草稿'}
                    </Tag>
                    {cr.difficulty && (
                      <Tag
                        color={
                          cr.difficulty >= 4
                            ? 'red'
                            : cr.difficulty >= 3
                            ? 'orange'
                            : 'green'
                        }
                        style={{ fontSize: 11 }}
                      >
                        {'★'.repeat(cr.difficulty)}
                      </Tag>
                    )}
                  </Space>
                </div>

                {/* 场景信息 */}
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {cr.sceneCount || 0} 个教学场景
                  </Text>
                  {cr.totalDurationSeconds > 0 && (
                    <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      · 约 {Math.round(cr.totalDurationSeconds / 60)} 分钟
                    </Text>
                  )}
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* 底部提示 */}
      {classrooms.length > 0 && (
        <div style={{ marginTop: 16, padding: 12, background: '#f6ffed', borderRadius: 8, border: '1px solid #b7eb8f' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            💡 <Text strong>提示：</Text>课堂支持三种来源——① 课程学习页的章级课堂（按知识点自动生成）；
            ② 课堂生成器的「知识点勾选生成」（聚合/批量）；③ 课堂生成器的「学段合作课堂」（教师发起）。
          </Text>
        </div>
      )}
    </div>
  );
};

export default ClassroomList;

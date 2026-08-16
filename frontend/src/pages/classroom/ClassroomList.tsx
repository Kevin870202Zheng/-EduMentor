import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
} from 'antd';
import {
  PlayCircleOutlined,
  ReloadOutlined,
  ExperimentOutlined,
} from '@ant-design/icons';
import { classroomApi } from '../../api/classroomApi';

const { Title, Text } = Typography;

interface ClassroomListProps {
  /** 当前课程 ID（受控组件，由课程学习中心传入） */
  courseId: string;
  /** 课程名称（显示用） */
  courseName?: string;
  /** 跳转到课堂生成 Tab 的回调 */
  onGoGenerate?: () => void;
}

/**
 * 课堂列表（受控组件）— 课程学习中心 Tab2
 *
 * 展示当前课程已有的 AI 课堂。点击「进入学习」进入沉浸式课堂播放器。
 */
const ClassroomList: React.FC<ClassroomListProps> = ({
  courseId,
  courseName,
  onGoGenerate,
}) => {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [classrooms, setClassrooms] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (courseId) {
      loadClassrooms();
    } else {
      setLoading(false);
    }
  }, [courseId]);

  const loadClassrooms = async () => {
    if (!courseId) return;
    try {
      setLoading(true);
      setError(null);
      const list = await classroomApi.listClassrooms(courseId);
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

  if (!courseId) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message="请先选择课程"
          description="在左侧边栏顶部的课程选择器中，选择一门课程后再查看课堂。"
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
          <Title level={5} style={{ margin: 0 }}>
            🎓 智慧课堂
          </Title>
          <Text type="secondary">
            {courseName || ''}
            {classrooms.length > 0 ? ` · ${classrooms.length} 个课堂` : ''}
          </Text>
        </div>
        <Space>
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
          <Empty description="暂无课堂" image={Empty.PRESENTED_IMAGE_SIMPLE}>
            <Space direction="vertical" style={{ textAlign: 'center' }}>
              <Text type="secondary">
                切到「课堂生成」Tab，勾选知识点创建你的第一个智慧课堂
              </Text>
              <Button type="primary" icon={<ExperimentOutlined />} onClick={onGoGenerate}>
                去生成课堂
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
                    {cr.source && (
                      <Tag color="purple" style={{ fontSize: 11 }}>
                        {cr.source === 'collaborative' ? '协作' : cr.source === 'multi_knowledge' ? '聚合' : '知识点'}
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
    </div>
  );
};

export default ClassroomList;

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  List,
  Typography,
  Tag,
  Button,
  Progress,
  Space,
  Spin,
  Empty,
  Row,
  Col,
  Statistic,
} from 'antd';
import {
  PlayCircleOutlined,
  CheckCircleOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { classroomApi } from '../../api/classroomApi';

const { Title, Text } = Typography;

/**
 * 课堂历史记录页面
 * 展示学生已完成和进行中的课堂，支持断点续播
 */
const ClassroomHistory: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [classrooms, setClassrooms] = useState<any[]>([]);
  const [stats, setStats] = useState({
    total: 0,
    completed: 0,
    inProgress: 0,
  });

  useEffect(() => {
    loadHistory();
  }, []);

  const loadHistory = async () => {
    try {
      setLoading(true);
      // TODO: 从后端获取学生的课堂历史记录
      // 这里先使用示例数据
      setClassrooms([]);
    } finally {
      setLoading(false);
    }
  };

  const handleResume = (classroomId: string) => {
    navigate(`/student/classroom/${classroomId}`);
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Title level={4} style={{ marginBottom: 24 }}>📚 我的课堂记录</Title>

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总课堂数"
              value={stats.total}
              prefix={<BarChartOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已完成"
              value={stats.completed}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="进行中"
              value={stats.inProgress}
              prefix={<PlayCircleOutlined style={{ color: '#1677ff' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* 课堂列表 */}
      <Card title="课堂列表" bodyStyle={{ padding: 0 }}>
        {classrooms.length === 0 ? (
          <Empty
            description="暂无课堂记录"
            style={{ padding: 48 }}
          >
            <Text type="secondary">
              完成课堂学习后，这里将展示您的学习记录
            </Text>
          </Empty>
        ) : (
          <List
            dataSource={classrooms}
            renderItem={(item: any) => (
              <List.Item
                style={{ padding: '16px 24px' }}
                actions={[
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={() => handleResume(item.id)}
                    size="small"
                  >
                    {item.status === 'completed' ? '重新学习' : '继续学习'}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{item.title}</Text>
                      <Tag color={
                        item.status === 'completed' ? 'success' :
                        item.status === 'in_progress' ? 'processing' : 'default'
                      }>
                        {item.status === 'completed' ? '已完成' :
                         item.status === 'in_progress' ? '学习中' : '未开始'}
                      </Tag>
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={4}>
                      <Text type="secondary">{item.description}</Text>
                      <Space size={24}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          场景进度: {item.scenesCompleted}/{item.totalScenes}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Quiz正确率: {item.quizTotalCount > 0
                            ? Math.round((item.quizCorrectCount / item.quizTotalCount) * 100)
                            : 0}%
                        </Text>
                      </Space>
                      <Progress
                        percent={item.totalScenes > 0
                          ? Math.round((item.scenesCompleted / item.totalScenes) * 100)
                          : 0}
                        size="small"
                        style={{ maxWidth: 300 }}
                      />
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </div>
  );
};

export default ClassroomHistory;

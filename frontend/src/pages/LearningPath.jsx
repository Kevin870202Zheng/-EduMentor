import { useState, useEffect } from 'react';
import { Card, Tag, Typography, Spin, Steps, Button, Empty, Radio } from 'antd';
import { CheckCircleOutlined, ClockCircleOutlined, RightCircleOutlined } from '@ant-design/icons';
import { pathAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

const DEMO_COURSE_ID = '1';

const DEMO_DATA = {
  strategy: 'balanced',
  total_kps: 15,
  total_estimated_minutes: 360,
  path: [
    { kp_id: 'kp-1', name: '函数基本概念', priority: 0.85, mastery: 0.35, estimated_minutes: 25 },
    { kp_id: 'kp-2', name: '极限与连续', priority: 0.82, mastery: 0.40, estimated_minutes: 30 },
    { kp_id: 'kp-3', name: '导数与微分', priority: 0.78, mastery: 0.50, estimated_minutes: 35 },
    { kp_id: 'kp-4', name: '积分基本定理', priority: 0.70, mastery: 0.55, estimated_minutes: 30 },
    { kp_id: 'kp-5', name: '不定积分', priority: 0.65, mastery: 0.60, estimated_minutes: 25 },
  ]
};

export default function LearningPath() {
  const [loading, setLoading] = useState(true);
  const [pathData, setPathData] = useState(null);
  const [strategy, setStrategy] = useState('balanced');
  const { user } = useAuth();
  const studentId = user?.id || '2';

  useEffect(() => {
    loadPath();
  }, [strategy]);

  const loadPath = async () => {
    setLoading(true);
    try {
      const res = await pathAPI.getPlan(studentId, DEMO_COURSE_ID, strategy);
      setPathData(res.data || res);
    } catch (err) {
      setPathData({
        ...DEMO_DATA,
        strategy,
      });
    }
    setLoading(false);
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>🗺️ 个性化学习路径</Title>
        <Radio.Group value={strategy} onChange={e => setStrategy(e.target.value)} buttonStyle="solid">
          <Radio.Button value="balanced">均衡推荐</Radio.Button>
          <Radio.Button value="shortest">最短路径</Radio.Button>
          <Radio.Button value="explore">拓展探索</Radio.Button>
        </Radio.Group>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Text type="secondary">
          共 <Text strong>{pathData?.total_kps || 0}</Text> 个知识点，
          预计总学习时间 <Text strong>{pathData?.total_estimated_minutes || 0}</Text> 分钟
        </Text>
      </Card>

      <Steps
        direction="vertical"
        size="small"
        current={0}
        items={pathData?.path?.map((item, idx) => ({
          title: (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
              <Text strong>{item.name}</Text>
              <div>
                <Tag color={item.mastery < 0.4 ? 'red' : item.mastery < 0.7 ? 'orange' : 'green'}>
                  掌握度 {Math.round((item.mastery || 0) * 100)}%
                </Tag>
                <Tag icon={<ClockCircleOutlined />}>{item.estimated_minutes || 25}分钟</Tag>
              </div>
            </div>
          ),
          description: `优先级 ${Math.round((item.priority || 0) * 100)}分 · 建议优先学习`,
          status: idx === 0 ? 'process' : 'wait',
          icon: idx === 0 ? <RightCircleOutlined style={{ color: '#1677ff' }} /> :
                item.mastery >= 0.8 ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> :
                <ClockCircleOutlined style={{ color: '#999' }} />,
        })) || []}
      />

      {!pathData?.path?.length && <Empty description="暂无学习路径数据" />}
    </div>
  );
}

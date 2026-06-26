import { useState, useEffect, useCallback } from 'react';
import { Card, Tag, Typography, Spin, Steps, Empty, Radio, message } from 'antd';
import { CheckCircleOutlined, ClockCircleOutlined, RightCircleOutlined } from '@ant-design/icons';
import { pathAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOutletContext } from 'react-router-dom';

const { Title, Text } = Typography;

const STRATEGY_MAP = {
  balanced: 'REORDER',
  shortest: 'SHORTEN',
  explore: 'FOCUS_WEAK',
};

export default function LearningPath() {
  const [loading, setLoading] = useState(false);
  const [pathData, setPathData] = useState(null);
  const [pathId, setPathId] = useState(null);
  const [strategy, setStrategy] = useState('balanced');
  const [error, setError] = useState(null);
  const { user } = useAuth();
  const { selectedCourseId } = useOutletContext();
  const studentId = user?.id;

  // Initial load
  useEffect(() => {
    if (selectedCourseId) loadPath();
    else setLoading(false);
  }, [selectedCourseId]);

  // 页面可见性变化时自动刷新
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'visible' && selectedCourseId) {
        loadPath();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [selectedCourseId]);

  const loadPath = useCallback(async () => {
    if (!selectedCourseId) return;
    setLoading(true);
    setError(null);
    setPathId(null);
    try {
      const res = await pathAPI.getPlan(studentId, selectedCourseId, strategy);
      const payload = res.data?.data || res.data || res;
      setPathData(payload);
      if (payload?.id) setPathId(payload.id);
    } catch (err) {
      setError('暂无学习路径数据');
    }
    setLoading(false);
  }, [studentId, selectedCourseId]);

  // 切换策略时适配已有路径，而非重建
  const handleStrategyChange = useCallback(async (newStrategy) => {
    setStrategy(newStrategy);
    if (!pathId) {
      // 没有路径时直接重新规划
      loadPath();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const backendStrategy = STRATEGY_MAP[newStrategy] || 'REORDER';
      const res = await pathAPI.adapt(pathId, backendStrategy);
      const payload = res.data?.data || res.data || res;
      setPathData(payload);
      if (payload?.id) setPathId(payload.id);
    } catch (err) {
      message.warning('策略适配失败，重新规划路径');
      loadPath();
    }
    setLoading(false);
  }, [pathId, loadPath]);

  // 估算总时间
  const totalMinutes = pathData?.nodes?.reduce((sum, n) => sum + (n.estimatedMinutes || 25), 0) || 0;

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  if (!selectedCourseId) return <Empty description="请先选择一门课程" />;
  if (error) return <Empty description={error} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>🗺️ 个性化学习路径</Title>
        <Radio.Group value={strategy} onChange={e => handleStrategyChange(e.target.value)} buttonStyle="solid">
          <Radio.Button value="balanced">均衡推荐</Radio.Button>
          <Radio.Button value="shortest">最短路径</Radio.Button>
          <Radio.Button value="explore">拓展探索</Radio.Button>
        </Radio.Group>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Text type="secondary">
          共 <Text strong>{pathData?.totalNodes || 0}</Text> 个知识点，
          预计总学习时间 <Text strong>{totalMinutes}</Text> 分钟
          {pathData?.adaptStrategy && (
            <Tag style={{ marginLeft: 8 }} color="blue">
              当前策略: {pathData.adaptStrategy === 'REORDER' ? '均衡推荐' : pathData.adaptStrategy === 'SHORTEN' ? '最短路径' : '拓展探索'}
            </Tag>
          )}
        </Text>
      </Card>

      <Steps
        direction="vertical"
        size="small"
        current={0}
        items={pathData?.nodes?.map((item, idx) => ({
          title: (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
              <Text strong>{item.knowledgePointName || '未知知识点'}</Text>
              <div>
                <Tag color={item.masteryThreshold < 0.4 ? 'red' : item.masteryThreshold < 0.7 ? 'orange' : 'green'}>
                  掌握度 {Math.round((item.masteryThreshold || 0) * 100)}%
                </Tag>
                <Tag icon={<ClockCircleOutlined />}>{item.estimatedMinutes || 25}分钟</Tag>
              </div>
            </div>
          ),
          description: `优先级 ${Math.round(((idx === 0 ? 1 : 1 / (idx + 1)) * 100))}分 · 建议优先学习`,
          status: idx === 0 ? 'process' : item.status === 'COMPLETED' ? 'finish' : 'wait',
          icon: idx === 0 ? <RightCircleOutlined style={{ color: '#1677ff' }} /> :
                item.status === 'COMPLETED' ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> :
                <ClockCircleOutlined style={{ color: '#999' }} />,
        })) || []}
      />

      {!pathData?.nodes?.length && <Empty description="暂无学习路径数据" />}
    </div>
  );
}

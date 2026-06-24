import { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Spin, Typography, Alert, Progress, Tag, Empty } from 'antd';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { RadarChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { diagnosisAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { BookOutlined, TrophyOutlined, ThunderboltOutlined, WarningOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';

echarts.use([RadarChart, TooltipComponent, LegendComponent, CanvasRenderer]);

const { Title, Text } = Typography;

// TODO: 支持多课程 — 当前从 URL 查询参数 ?courseId=xxx 获取课程 ID，
//       后续可从用户设置/课程选择器中动态获取
const DEFAULT_COURSE_ID = '1';

// 默认雷达数据
const DEFAULT_RADAR = {
  dimensions: [
    { name: '知识掌握度', value: 65 },
    { name: '认知能力', value: 70 },
    { name: '元认知水平', value: 55 },
    { name: '学习投入度', value: 60 },
  ]
};

// 默认画像
const DEFAULT_PROFILE = {
  cognitive_profile: { avg_mastery: 0.65, total_kps: 20, weak_kps: 8, mastered_kps: 12 },
  knowledge_details: [],
  weak_points: [],
  alert_status: { level: 'none', composite_score: 0.7 }
};

export default function StudentDashboard() {
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [radarData, setRadarData] = useState(null);
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const studentId = user?.id || '2';
  const courseId = searchParams.get('courseId') || DEFAULT_COURSE_ID;

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [profileRes, radarRes] = await Promise.all([
        diagnosisAPI.analyze({ student_id: String(studentId), course_id: courseId })
          .catch(() => ({ data: DEFAULT_PROFILE })),
        diagnosisAPI.getRadar(studentId)
          .catch(() => ({ data: DEFAULT_RADAR })),
      ]);
      setProfile(profileRes.data || profileRes);
      setRadarData(radarRes.data || radarRes);
    } catch (err) {
      console.log('Using demo data');
      setProfile(DEFAULT_PROFILE);
      setRadarData(DEFAULT_RADAR);
    }
    setLoading(false);
  };

  const radarOption = radarData ? {
    radar: {
      indicator: radarData.dimensions.map(d => ({ name: d.name, max: 100 })),
      shape: 'circle',
      splitArea: { areaStyle: { color: ['rgba(22, 119, 255, 0.02)', 'rgba(22, 119, 255, 0.05)'] } },
      axisLine: { lineStyle: { color: 'rgba(22, 119, 255, 0.3)' } },
    },
    series: [{
      type: 'radar',
      data: [{ value: radarData.dimensions.map(d => d.value), name: '当前学情', areaStyle: { color: 'rgba(22, 119, 255, 0.2)' } }],
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { color: '#1677ff', width: 2 },
    }],
  } : {};

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  const cp = profile?.cognitive_profile || { avg_mastery: 0, weak_kps: 0, mastered_kps: 0, total_kps: 0 };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📊 我的学情总览</Title>
        {user?.real_name && <Text type="secondary">欢迎，{user.real_name}</Text>}
      </div>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="知识掌握度"
              value={Math.round((cp.avg_mastery || 0.65) * 100)}
              suffix="%"
              prefix={<BookOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已掌握知识点"
              value={cp.mastered_kps || 12}
              suffix={`/ ${cp.total_kps || 20}`}
              prefix={<TrophyOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="薄弱知识点"
              value={cp.weak_kps || 8}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="预警状态"
              value={profile?.alert_status?.level === 'none' ? '正常' : '需关注'}
              prefix={<WarningOutlined />}
              valueStyle={{ color: profile?.alert_status?.level === 'none' ? '#52c41a' : '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="🧠 四维学情雷达" style={{ height: 400 }}>
            {radarData ? (
              <ReactEChartsCore echarts={echarts} option={radarOption} style={{ height: 300 }} />
            ) : (
              <Empty description="暂无雷达数据" />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="📋 今日学习建议" style={{ height: 400 }}>
            {profile?.weak_points?.length > 0 ? profile.weak_points.slice(0, 5).map((wp, i) => (
              <div key={i} style={{ marginBottom: 12, padding: 8, background: '#fff7e6', borderRadius: 6 }}>
                <Text strong>{wp.kp_id || `知识点 ${i + 1}`}</Text>
                <Progress percent={Math.round((wp.mastery || 0) * 100)} size="small" status="active" />
                <Tag color="orange" style={{ marginTop: 4 }}>建议优先复习</Tag>
              </div>
            )) : (
              <div>
                <Empty description="暂无数据，完成测评后可查看个性化建议" />
                <Alert
                  style={{ marginTop: 16 }}
                  type="info"
                  showIcon
                  message="提示"
                  description="完成一次测评或答题后，系统将自动分析你的学情数据。"
                />
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

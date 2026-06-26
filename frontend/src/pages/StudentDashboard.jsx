import { useState, useEffect, useCallback } from 'react';
import { Row, Col, Card, Statistic, Spin, Typography, Alert, Progress, Tag, Empty, Button, Space } from 'antd';
import ReactECharts from 'echarts-for-react';
import * as echarts from 'echarts/core';
import { RadarChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { diagnosisAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { BookOutlined, TrophyOutlined, ThunderboltOutlined, WarningOutlined, RightCircleOutlined } from '@ant-design/icons';
import { useOutletContext, useNavigate } from 'react-router-dom';

echarts.use([RadarChart, TooltipComponent, LegendComponent, CanvasRenderer]);

const { Title, Text } = Typography;

export default function StudentDashboard() {
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [radarData, setRadarData] = useState(null);
  const [error, setError] = useState(null);
  const { user } = useAuth();
  const { selectedCourseId, studentCourses } = useOutletContext();
  const navigate = useNavigate();
  const studentId = user?.id;

  const currentCourse = studentCourses?.find(c => c.courseId === selectedCourseId);

  useEffect(() => {
    if (selectedCourseId) {
      loadData();
    } else {
      setLoading(false);
    }
  }, [selectedCourseId]);

  // 页面可见性变化时自动刷新
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'visible' && selectedCourseId) {
        loadData();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [selectedCourseId]);

  const loadData = useCallback(async () => {
    try {
      const [profileRes, radarRes] = await Promise.all([
        diagnosisAPI.analyze({ student_id: studentId, course_id: selectedCourseId }),
        diagnosisAPI.getRadar(studentId, selectedCourseId),
      ]);
      setProfile(profileRes?.data || profileRes);
      setRadarData(radarRes?.data || radarRes);
      setError(null);
    } catch (err) {
      setError('暂无学情数据，请先完成一些练习');
    }
    setLoading(false);
  }, [studentId, selectedCourseId]);

  const hasData = profile && (profile.totalQuestions > 0);

  const radarOption = radarData?.dimensions ? {
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
  } : null;

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  if (!selectedCourseId) return <Empty description="请先选择一门课程" />;
  if (error && !hasData) return <Alert type="info" message={error} style={{ margin: 24 }} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📊 我的学情总览</Title>
        <Space>
          {user?.real_name && <Text type="secondary">欢迎，{user.real_name}</Text>}
          {currentCourse?.courseCode && (
            <Button type="primary" size="small" icon={<RightCircleOutlined />}
              onClick={() => navigate(`/student/learning/${currentCourse.courseCode}`)}>
              去学习
            </Button>
          )}
        </Space>
      </div>

      {!hasData ? (
        <Card>
          <Empty description="暂无学情数据">
            <Text type="secondary">完成答题后，系统将自动分析你的学情数据。</Text>
            <div style={{ marginTop: 12 }}>
              {currentCourse?.courseCode && (
                <Button type="primary" icon={<RightCircleOutlined />}
                  onClick={() => navigate(`/student/learning/${currentCourse.courseCode}`)}>
                  开始学习
                </Button>
              )}
            </div>
          </Empty>
        </Card>
      ) : (
        <>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}>
              <Card>
                <Statistic
                  title="正确率"
                  value={profile.accuracyRate != null ? Math.round(profile.accuracyRate * 100) : '-'}
                  suffix="%"
                  prefix={<BookOutlined />}
                  valueStyle={{ color: '#1677ff' }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="总答题数"
                  value={profile.totalQuestions || 0}
                  prefix={<TrophyOutlined />}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="薄弱知识点"
                  value={profile.weakKpCount ?? '-'}
                  prefix={<ThunderboltOutlined />}
                  valueStyle={{ color: profile.weakKpCount > 0 ? '#faad14' : '#52c41a' }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic
                  title="优势知识点"
                  value={profile.strongKpCount ?? '-'}
                  prefix={<WarningOutlined />}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Card title="🧠 四维学情雷达" style={{ height: 400 }}>
                {radarOption ? (
                  <ReactECharts echarts={echarts} option={radarOption} style={{ height: 300 }} />
                ) : (
                  <Empty description="暂无雷达数据" />
                )}
              </Card>
            </Col>
            <Col span={12}>
              <Card title="📋 今日学习建议" style={{ height: 400 }}>
                {profile.topWeakKps?.length > 0 ? (
                  <>
                    {profile.topWeakKps.slice(0, 5).map((wp, i) => (
                      <div key={i} style={{ marginBottom: 12, padding: 8, background: '#fff7e6', borderRadius: 6 }}>
                        <Text strong>{wp.knowledgePointName || `知识点 ${i + 1}`}</Text>
                        <Progress percent={wp.masteryLevel != null ? Math.round(wp.masteryLevel * 100) : 0} size="small" status="active" />
                        <Tag color="orange" style={{ marginTop: 4 }}>建议优先复习</Tag>
                      </div>
                    ))}
                    <div style={{ marginTop: 8 }}>
                      <Button type="link" size="small" onClick={() => navigate(`/student/learning/${currentCourse?.courseCode}`)}>
                        去学习这些知识点 →
                      </Button>
                    </div>
                  </>
                ) : (
                  <div>
                    <Empty description="暂无薄弱知识点" />
                    {profile.recommendations?.length > 0 && (
                      <Alert style={{ marginTop: 12 }} type="success" showIcon message="建议"
                        description={profile.recommendations[0]} />
                    )}
                  </div>
                )}
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}

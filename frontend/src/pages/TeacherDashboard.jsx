import { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Table, Tag, Typography, Spin, Progress, Alert, Button, Space, Select } from 'antd';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart, PieChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { dashboardAPI, courseAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { TeamOutlined, BookOutlined, WarningOutlined, TrophyOutlined, RiseOutlined, FileTextOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';

echarts.use([BarChart, PieChart, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer]);

const { Title, Text } = Typography;

const DEMO_SUMMARY = {
  class_overview: { total_students: 45, class_avg_mastery: 68.5, total_knowledge_points: 20, weak_students: 8, weak_student_ratio: 17.8 },
  alert_statistics: { blue: 5, yellow: 3, red: 1, none: 36 },
  common_errors: [
    { type: 'knowledge', count: 28 }, { type: 'method', count: 15 }, { type: 'calculation', count: 12 }, { type: 'metacognition', count: 8 }
  ],
};

const DEMO_STUDENTS = Array.from({ length: 10 }, (_, i) => ({
  student_id: `stu-${1001 + i}`, name: `学生${i + 1}`,
  avg_mastery: 50 + Math.random() * 40,
  alert_level: ['none', 'none', 'blue', 'none', 'yellow', 'none', 'none', 'red', 'blue', 'none'][i],
  error_count: Math.floor(Math.random() * 15),
  meta_cognition: 0.3 + Math.random() * 0.5,
}));

export default function TeacherDashboard() {
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState(DEMO_SUMMARY);
  const [students, setStudents] = useState(DEMO_STUDENTS);
  const [myCourses, setMyCourses] = useState([]);
  const [selectedCourseId, setSelectedCourseId] = useState(null);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    loadCourses();
  }, []);

  useEffect(() => {
    if (selectedCourseId) {
      loadData(selectedCourseId);
    }
  }, [selectedCourseId]);

  const loadCourses = async () => {
    try {
      const res = await courseAPI.listByTeacher(user?.id);
      const list = res?.data || res || [];
      setMyCourses(list);
      // Auto-select first course or from URL param
      const urlCourseId = searchParams.get('courseId');
      const defaultId = urlCourseId || (list.length > 0 ? list[0].id : null);
      if (defaultId) setSelectedCourseId(defaultId);
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  const loadData = async (courseId) => {
    try {
      const res = await dashboardAPI.getSummary(courseId);
      if (res?.data) setSummary(res.data);
    } catch (err) { /* use demo */ }
  };

  const selectedCourse = myCourses.find(c => c.id === selectedCourseId);

  const alertPieOption = {
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: true, formatter: '{b}: {c}' },
      data: [
        { value: summary.alert_statistics.none, name: '正常', itemStyle: { color: '#52c41a' } },
        { value: summary.alert_statistics.blue, name: '蓝色预警', itemStyle: { color: '#1677ff' } },
        { value: summary.alert_statistics.yellow, name: '黄色预警', itemStyle: { color: '#faad14' } },
        { value: summary.alert_statistics.red, name: '红色预警', itemStyle: { color: '#ff4d4f' } },
      ]
    }]
  };

  const errorBarOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: summary.common_errors.map(e => ({ knowledge: '知识型', method: '方法型', calculation: '计算型', metacognition: '元认知型' }[e.type] || e.type)) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: summary.common_errors.map(e => e.count), itemStyle: { borderRadius: [6, 6, 0, 0] }, color: '#1677ff' }],
  };

  const columns = [
    { title: '姓名', dataIndex: 'name', key: 'name' },
    { title: '掌握度', dataIndex: 'avg_mastery', key: 'avg_mastery', render: (v) => <Progress percent={Math.round(v)} size="small" /> },
    { title: '预警', dataIndex: 'alert_level', key: 'alert_level', render: (v) => { const colors = { none: 'green', blue: 'blue', yellow: 'orange', red: 'red' }; return <Tag color={colors[v] || 'green'}>{v === 'none' ? '正常' : `${v}级预警`}</Tag>; } },
    { title: '错题数', dataIndex: 'error_count', key: 'error_count' },
    { title: '元认知', dataIndex: 'meta_cognition', key: 'meta_cognition', render: (v) => <Text>{Math.round(v * 100)}%</Text> },
  ];

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📊 教学智慧驾驶舱</Title>
        <Space>
          <Select
            style={{ width: 220 }}
            placeholder="选择课程"
            value={selectedCourseId}
            onChange={setSelectedCourseId}
            options={myCourses.map(c => ({ label: `[${c.courseCode}] ${c.name}`, value: c.id }))}
          />
          <Button icon={<FileTextOutlined />} onClick={() => navigate('/teacher/courses')}>
            课程管理
          </Button>
          {user?.real_name && <Text type="secondary">{user.real_name} 老师</Text>}
        </Space>
      </div>

      {selectedCourse && (
        <Card size="small" style={{ marginBottom: 12 }}>
          <Text strong>{selectedCourse.name}</Text>
          <Text type="secondary" style={{ marginLeft: 12 }}>编号: {selectedCourse.courseCode}</Text>
          <Button type="link" size="small" style={{ marginLeft: 12 }} onClick={() => navigate(`/teacher/courses/${selectedCourse.courseCode}/manage`)}>
            内容管理 →
          </Button>
        </Card>
      )}

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={4}><Card size="small"><Statistic title="班级人数" value={summary.class_overview.total_students} prefix={<TeamOutlined />} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="平均掌握度" value={summary.class_overview.class_avg_mastery} suffix="%" prefix={<RiseOutlined />} valueStyle={{ color: '#1677ff' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="薄弱学生" value={summary.class_overview.weak_students} suffix={`/ ${summary.class_overview.total_students}`} prefix={<WarningOutlined />} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="知识点" value={summary.class_overview.total_knowledge_points} prefix={<BookOutlined />} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="需关注" value={summary.alert_statistics.blue + summary.alert_statistics.yellow + summary.alert_statistics.red} prefix={<TrophyOutlined />} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}><Card title="🚨 预警分布" size="small"><ReactEChartsCore echarts={echarts} option={alertPieOption} style={{ height: 220 }} /></Card></Col>
        <Col span={8}><Card title="📈 共性错误分布" size="small"><ReactEChartsCore echarts={echarts} option={errorBarOption} style={{ height: 220 }} /></Card></Col>
        <Col span={8}>
          <Card title="💡 教学建议" size="small" style={{ height: 270 }}>
            <Alert type="warning" showIcon message="薄弱知识点" description="全班42%学生对「极限定义」掌握不足，建议安排专题复习课。" style={{ marginBottom: 8 }} />
            <Alert type="info" showIcon message="分层教学" description="建议按掌握度将学生分为3组，分别推送差异化练习。" />
          </Card>
        </Col>
      </Row>

      <Card title="👥 学生学情列表">
        <Table dataSource={students} columns={columns} rowKey="student_id" size="small" pagination={{ pageSize: 5 }} />
      </Card>
    </div>
  );
}

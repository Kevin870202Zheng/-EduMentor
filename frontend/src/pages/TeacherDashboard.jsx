import { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Table, Tag, Typography, Spin, Progress, Alert, Button, Space, Select } from 'antd';
import ReactECharts from 'echarts-for-react';
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
  studentId: `stu-${1001 + i}`, displayName: `学生${i + 1}`,
  avgMastery: 50 + Math.random() * 40,
  alertLevel: ['none', 'none', 'blue', 'none', 'yellow', 'none', 'none', 'red', 'blue', 'none'][i],
  errorCount: Math.floor(Math.random() * 15),
  metaCognition: 0.3 + Math.random() * 0.5,
  className: ['计科2101', '计科2101', '计科2102', '计科2101', '计科2102', '计科2103', '计科2102', '计科2103', '计科2101', '计科2103'][i],
}));

export default function TeacherDashboard() {
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState(DEMO_SUMMARY);
  const [students, setStudents] = useState(DEMO_STUDENTS);
  const [myCourses, setMyCourses] = useState([]);
  const [selectedCourseId, setSelectedCourseId] = useState(null);
  const [selectedClass, setSelectedClass] = useState(null);
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
      const urlCourseId = searchParams.get('courseId');
      const defaultId = urlCourseId || (list.length > 0 ? list[0].id : null);
      if (defaultId) setSelectedCourseId(defaultId);
    } catch (e) { /* ignore */ }
    setLoading(false);
  };

  const loadData = async (courseId) => {
    try {
      const [sumRes, stuRes] = await Promise.all([
        dashboardAPI.getSummary(courseId),
        dashboardAPI.getStudentList(courseId),
      ]);
      if (sumRes?.data) setSummary(sumRes.data);
      if (stuRes?.data?.items) setStudents(stuRes.data.items);
    } catch (err) { /* use demo data as fallback */ }
  };

  const selectedCourse = myCourses.find(c => c.id === selectedCourseId);

  // 提取班级列表用于筛选
  const classList = [...new Set(students.map(s => s.className).filter(Boolean))];
  const filteredStudents = selectedClass
    ? students.filter(s => s.className === selectedClass)
    : students;

  const alertPieOption = {
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: true, formatter: '{b}: {c}' },
      data: [
        { value: summary.alert_statistics?.none || 0, name: '正常', itemStyle: { color: '#52c41a' } },
        { value: summary.alert_statistics?.blue || 0, name: '蓝色预警', itemStyle: { color: '#1677ff' } },
        { value: summary.alert_statistics?.yellow || 0, name: '黄色预警', itemStyle: { color: '#faad14' } },
        { value: summary.alert_statistics?.red || 0, name: '红色预警', itemStyle: { color: '#ff4d4f' } },
      ]
    }]
  };

  const errorBarOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (summary.common_errors || []).map(e => ({ knowledge: '知识型', method: '方法型', calculation: '计算型', metacognition: '元认知型' }[e.type] || e.type)) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: (summary.common_errors || []).map(e => e.count), itemStyle: { borderRadius: [6, 6, 0, 0] }, color: '#1677ff' }],
  };

  const columns = [
    { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
    { title: '班级', dataIndex: 'className', key: 'className', width: 100, render: (v) => v ? <Tag>{v}</Tag> : '-' },
    { title: '掌握度', dataIndex: 'correctRate', key: 'correctRate', render: (v) => <Progress percent={Math.round(v || 0)} size="small" /> },
    { title: '预警', dataIndex: 'pendingAlertCount', key: 'pendingAlertCount', render: (v) => {
        const level = v >= 2 ? 'red' : v >= 1 ? 'orange' : 'green';
        return <Tag color={level}>{v > 0 ? `${v} 条预警` : '正常'}</Tag>;
      }
    },
    { title: '答题数', dataIndex: 'totalAnswers', key: 'totalAnswers' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v) => {
        const m = { active: { color: 'green', label: '活跃' }, inactive: { color: 'default', label: '不活跃' }, 'at-risk': { color: 'red', label: '高危' } };
        const t = m[v] || { color: 'default', label: v };
        return <Tag color={t.color}>{t.label}</Tag>;
      }
    },
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
          {classList.length > 0 && (
            <Select
              style={{ width: 150 }}
              placeholder="全部班级"
              allowClear
              value={selectedClass}
              onChange={setSelectedClass}
              options={classList.map(c => ({ label: c, value: c }))}
            />
          )}
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
        <Col span={4}><Card size="small"><Statistic title="班级人数" value={summary.class_overview?.total_students || 0} prefix={<TeamOutlined />} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="平均掌握度" value={summary.class_overview?.class_avg_mastery || 0} suffix="%" prefix={<RiseOutlined />} valueStyle={{ color: '#1677ff' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="薄弱学生" value={summary.class_overview?.weak_students || 0} suffix={`/ ${summary.class_overview?.total_students || 0}`} prefix={<WarningOutlined />} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="知识点" value={summary.class_overview?.total_knowledge_points || 0} prefix={<BookOutlined />} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="需关注" value={(summary.alert_statistics?.blue || 0) + (summary.alert_statistics?.yellow || 0) + (summary.alert_statistics?.red || 0)} prefix={<TrophyOutlined />} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}><Card title="🚨 预警分布" size="small"><ReactECharts echarts={echarts} option={alertPieOption} style={{ height: 220 }} /></Card></Col>
        <Col span={8}><Card title="📈 共性错误分布" size="small"><ReactECharts echarts={echarts} option={errorBarOption} style={{ height: 220 }} /></Card></Col>
        <Col span={8}>
          <Card title="💡 教学建议" size="small" style={{ height: 270 }}>
            <Alert type="warning" showIcon message="薄弱知识点" description="全班学生对部分知识点掌握不足，建议安排专题复习课。" style={{ marginBottom: 8 }} />
            <Alert type="info" showIcon message="分层教学" description="建议按掌握度将学生分为3组，分别推送差异化练习。" />
          </Card>
        </Col>
      </Row>

      <Card title="👥 学生学情列表">
        <Table dataSource={filteredStudents} columns={columns} rowKey="studentId" size="small" pagination={{ pageSize: 5 }} />
      </Card>
    </div>
  );
}

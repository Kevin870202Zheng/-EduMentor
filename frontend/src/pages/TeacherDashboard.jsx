import { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Table, Tag, Typography, Spin, Progress, Alert, Button, Space, Select } from 'antd';
import ReactECharts from 'echarts-for-react';
import * as echarts from 'echarts/core';
import { BarChart, PieChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { dashboardAPI, courseAPI, enrollmentAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { TeamOutlined, BookOutlined, WarningOutlined, TrophyOutlined, RiseOutlined, FileTextOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';

echarts.use([BarChart, PieChart, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer]);

const { Title, Text } = Typography;

export default function TeacherDashboard() {
  const [loading, setLoading] = useState(true);
  const [overview, setOverview] = useState(null);
  const [students, setStudents] = useState([]);
  const [myCourses, setMyCourses] = useState([]);
  const [courseCounts, setCourseCounts] = useState({});
  const [selectedCourseId, setSelectedCourseId] = useState(null);
  const [selectedClass, setSelectedClass] = useState(null);
  const [suggestions, setSuggestions] = useState([]);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => { loadCourses(); }, []);

  useEffect(() => {
    if (selectedCourseId) loadData(selectedCourseId);
  }, [selectedCourseId]);

  const loadCourses = async () => {
    try {
      const res = await courseAPI.listByTeacher(user?.id);
      const list = res?.data || res || [];
      // 查询每门课的学生数
      const courseCounts = {};
      await Promise.all(list.map(async (c) => {
        try {
          const cntRes = await enrollmentAPI.countByCourse(c.id);
          courseCounts[c.id] = cntRes?.data || 0;
        } catch (e) { courseCounts[c.id] = 0; }
      }));
      setCourseCounts(courseCounts);
      setMyCourses(list);
      const urlCourseId = searchParams.get('courseId');
      // 默认选中：URL 指定的 → 第一个有学生的 → 第一个课程
      const defaultId = urlCourseId
        || list.find(c => (courseCounts[c.id] || 0) > 0)?.id
        || (list.length > 0 ? list[0].id : null);
      if (defaultId) setSelectedCourseId(defaultId);
      else setLoading(false);
    } catch (e) { setLoading(false); }
  };

  const loadData = async (courseId) => {
    setLoading(true);
    try {
      const [sumRes, stuRes, sugRes] = await Promise.all([
        dashboardAPI.getSummary(courseId),
        dashboardAPI.getStudentList(courseId),
        dashboardAPI.getSuggestions(courseId).catch(() => ({ data: [] })),
      ]);
      if (sumRes?.data) setOverview(sumRes.data);
      if (stuRes?.data) {
        const items = stuRes.data.items || stuRes.data.content || stuRes.data || [];
        setStudents(items);
      }
      if (sugRes?.data) setSuggestions(sugRes.data);
    } catch (err) {
      console.error('Dashboard data load failed:', err);
    }
    setLoading(false);
  };

  const selectedCourse = myCourses.find(c => c.id === selectedCourseId);

  // Extract class list for filtering
  const classList = [...new Set(students.map(s => s.className).filter(Boolean))];
  const filteredStudents = selectedClass
    ? students.filter(s => s.className === selectedClass)
    : students;

  // Alert statistics
  const alertNormal = students.filter(s => !s.pendingAlertCount || s.pendingAlertCount === 0).length;
  const alertBlue = students.filter(s => s.pendingAlertCount === 1).length;
  const alertYellow = students.filter(s => s.pendingAlertCount === 2).length;
  const alertRed = students.filter(s => s.pendingAlertCount >= 3).length;

  const alertPieOption = {
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: true, formatter: '{b}: {c}' },
      data: [
        { value: alertNormal, name: '正常', itemStyle: { color: '#52c41a' } },
        { value: alertBlue, name: '蓝色预警', itemStyle: { color: '#1677ff' } },
        { value: alertYellow, name: '黄色预警', itemStyle: { color: '#faad14' } },
        { value: alertRed, name: '红色预警', itemStyle: { color: '#ff4d4f' } },
      ]
    }]
  };

  // Knowledge mastery bar chart
  const kpMastery = overview?.knowledgeMastery || [];
  const errorBarOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: kpMastery.map(k => k.knowledgePointName || ''), axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [{ type: 'bar', data: kpMastery.map(k => Math.round((k.masteryRate || 0) * 100)), itemStyle: { borderRadius: [6, 6, 0, 0] }, color: '#1677ff' }],
  };

  const columns = [
    { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
    { title: '班级', dataIndex: 'className', key: 'className', width: 100, render: (v) => v ? <Tag>{v}</Tag> : '-' },
    { title: '掌握度', key: 'mastery', width: 140, render: (_, r) => <Progress percent={Math.round(r.correctRate || 0)} size="small" /> },
    { title: '预警', dataIndex: 'pendingAlertCount', key: 'pendingAlertCount', render: (v) => {
        const level = v >= 3 ? 'red' : v >= 2 ? 'orange' : v >= 1 ? 'blue' : 'green';
        const label = v >= 3 ? `${v} 条预警` : v >= 1 ? `${v} 条预警` : '正常';
        return <Tag color={level}>{label}</Tag>;
      }
    },
    { title: '答题数', dataIndex: 'totalAnswers', key: 'totalAnswers' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v) => {
        const m = { active: { color: 'green', label: '活跃' }, inactive: { color: 'default', label: '不活跃' }, 'at-risk': { color: 'red', label: '高危' } };
        const t = m[v] || { color: 'default', label: v || '-' };
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
            options={myCourses.map(c => ({
              label: `[${c.courseCode}] ${c.name} (${courseCounts[c.id] || 0}人)`,
              value: c.id,
              disabled: !(courseCounts[c.id] || 0) > 0,
            }))}
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
        <Col span={4}><Card size="small"><Statistic title="班级人数" value={overview?.totalStudents || 0} prefix={<TeamOutlined />} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="平均正确率" value={Math.round(overview?.averageCorrectRate || 0)} suffix="%" prefix={<RiseOutlined />} valueStyle={{ color: '#1677ff' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="薄弱知识点" value={overview?.weakKnowledgeCount || 0} prefix={<WarningOutlined />} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="待处理预警" value={overview?.pendingAlertCount || 0} prefix={<TrophyOutlined />} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={5}><Card size="small"><Statistic title="本周答题" value={overview?.totalAnswersThisWeek || 0} prefix={<BookOutlined />} /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card title="🚨 预警分布" size="small">
            {students.length > 0 ? <ReactECharts echarts={echarts} option={alertPieOption} style={{ height: 220 }} /> : <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: 40 }}>暂无数据</Text>}
          </Card>
        </Col>
        <Col span={8}>
          <Card title="📈 知识点掌握度" size="small">
            {kpMastery.length > 0 ? <ReactECharts echarts={echarts} option={errorBarOption} style={{ height: 220 }} /> : <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: 40 }}>暂无数据</Text>}
          </Card>
        </Col>
        <Col span={8}>
          <Card title="💡 教学建议" size="small" style={{ height: 270 }}>
            {suggestions.length > 0 ? suggestions.slice(0, 3).map((s, i) => (
              <Alert key={i} type={s.level === 'warning' ? 'warning' : 'info'} showIcon message={s.title || s} style={{ marginBottom: 8 }} />
            )) : (
              <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: 20 }}>暂无教学建议</Text>
            )}
          </Card>
        </Col>
      </Row>

      <Card title="👥 学生学情列表">
        {students.length > 0 ? (
          <Table dataSource={filteredStudents} columns={columns} rowKey={r => r.studentId || r.id} size="small" pagination={{ pageSize: 5 }} />
        ) : (
          <div style={{ textAlign: 'center', padding: 40 }}><Text type="secondary">暂无学生数据</Text></div>
        )}
      </Card>
    </div>
  );
}

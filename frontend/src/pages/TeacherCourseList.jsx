import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Table, Button, Modal, Form, Input, Select, Tag, Space, Typography, Spin, message, Empty } from 'antd';
import { PlusOutlined, BookOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { courseAPI, enrollmentAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

// 学段选项（PRD v4.0 §5）
const STAGE_OPTIONS = [
  { label: '🏫 小学', value: 'PRIMARY' },
  { label: '📖 初中', value: 'JUNIOR' },
  { label: '🟢 高中', value: 'SENIOR' },
  { label: '🎓 大学', value: 'UNIVERSITY' },
];
const STAGE_NAMES = { PRIMARY: '小学', JUNIOR: '初中', SENIOR: '高中', UNIVERSITY: '大学' };
const STAGE_COLORS = { PRIMARY: 'blue', JUNIOR: 'orange', SENIOR: 'green', UNIVERSITY: 'purple' };

export default function TeacherCourseList() {
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [courseCounts, setCourseCounts] = useState({});
  const { user } = useAuth();
  const navigate = useNavigate();
  const [form] = Form.useForm();

  useEffect(() => {
    loadCourses();
  }, []);

  const loadCourses = async () => {
    setLoading(true);
    try {
      const res = await courseAPI.listByTeacher(user?.id);
      const courseList = res?.data || res || [];
      setCourses(courseList);
      // Load student counts for each course
      const counts = {};
      await Promise.all(courseList.map(async (c) => {
        try {
          const countRes = await enrollmentAPI.countByCourse(c.id);
          counts[c.id] = countRes?.data?.data ?? countRes?.data ?? 0;
        } catch (e) {
          counts[c.id] = 0;
        }
      }));
      setCourseCounts(counts);
    } catch (err) {
      message.error('加载课程列表失败');
    }
    setLoading(false);
  };

  const handleCreate = async (values) => {
    setSubmitting(true);
    try {
      await courseAPI.create(values);
      message.success(`课程「${values.name}」创建成功`);
      setCreateModalOpen(false);
      form.resetFields();
      loadCourses();
    } catch (err) {
      message.error(err?.message || '创建课程失败');
    }
    setSubmitting(false);
  };

  const columns = [
    {
      title: '课程编号',
      dataIndex: 'courseCode',
      key: 'courseCode',
      width: 120,
      render: (code) => <Tag color="blue">{code}</Tag>,
    },
    {
      title: '课程名称',
      dataIndex: 'name',
      key: 'name',
      render: (name, record) => (
        <a onClick={() => navigate(`/teacher/courses/${record.courseCode}/manage`)}>
          <BookOutlined style={{ marginRight: 6 }} />{name}
        </a>
      ),
    },
    {
      title: '学段',
      dataIndex: 'stage',
      key: 'stage',
      width: 90,
      render: (v) => v
        ? <Tag color={STAGE_COLORS[v] || 'default'}>{STAGE_NAMES[v] || v}</Tag>
        : <Tag color="default">未设置</Tag>,
    },
    {
      title: '学科',
      dataIndex: 'subject',
      key: 'subject',
      width: 100,
    },
    {
      title: '学生数',
      key: 'studentCount',
      width: 80,
      render: (_, record) => (
        <Text><TeamOutlined style={{ marginRight: 4 }} />{courseCounts[record.id] || 0}</Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'isPublished',
      key: 'isPublished',
      width: 80,
      render: (v) => v ? <Tag color="green">已发布</Tag> : <Tag color="orange">未发布</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            icon={<SettingOutlined />}
            onClick={() => navigate(`/teacher/courses/${record.courseCode}/manage`)}
          >
            内容管理
          </Button>
          <Button
            size="small"
            onClick={() => navigate(`/teacher/dashboard?courseId=${record.id}`)}
          >
            查看学情
          </Button>
        </Space>
      ),
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>📚 我的课程</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          创建课程
        </Button>
      </div>

      <Card>
        {courses.length === 0 ? (
          <Empty description="暂无课程，点击右上角创建第一门课程" />
        ) : (
          <Table
            dataSource={courses}
            columns={columns}
            rowKey="id"
            pagination={false}
            size="middle"
          />
        )}
      </Card>

      {/* 创建课程 Modal */}
      <Modal
        title="创建新课程"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="courseCode"
            label="课程编号"
            rules={[
              { required: true, message: '请输入课程编号' },
              { pattern: /^[A-Za-z0-9]{3,32}$/, message: '仅允许字母和数字，3-32位' },
            ]}
          >
            <Input placeholder="例如：MATH101" />
          </Form.Item>
          <Form.Item
            name="name"
            label="课程名称"
            rules={[{ required: true, message: '请输入课程名称' }]}
          >
            <Input placeholder="例如：高等数学（上）" />
          </Form.Item>
          <Form.Item
            name="subject"
            label="学科分类"
            rules={[{ required: true, message: '请选择学科' }]}
          >
            <Select placeholder="请选择学科">
              <Select.Option value="法律">法律</Select.Option>
              <Select.Option value="数学">数学</Select.Option>
              <Select.Option value="计算机">计算机</Select.Option>
              <Select.Option value="物理">物理</Select.Option>
              <Select.Option value="英语">英语</Select.Option>
              <Select.Option value="化学">化学</Select.Option>
              <Select.Option value="生物">生物</Select.Option>
              <Select.Option value="其他">其他</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="stage" label="学段" tooltip="课程所属学段，创建后可在内容管理中一键标注知识点学段">
            <Select options={STAGE_OPTIONS} placeholder="选择学段（如：大学）" allowClear />
          </Form.Item>
          <Form.Item name="gradeLevel" label="适用年级">
            <Input placeholder="例如：大一" />
          </Form.Item>
          <Form.Item name="description" label="课程描述">
            <Input.TextArea rows={3} placeholder="课程简介" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

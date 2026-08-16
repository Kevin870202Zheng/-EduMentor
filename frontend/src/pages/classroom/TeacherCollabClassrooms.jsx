import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card, Button, Modal, Form, Input, Select, Tag, Table, Space,
  Typography, message, Empty, Progress,
} from 'antd';
import { PlusOutlined, TeamOutlined, RightOutlined } from '@ant-design/icons';
import { collabApi } from '../../api/collabApi';
import { courseAPI } from '../../services/api';

const { Title, Text } = Typography;

const STATUS_META = {
  DRAFT: { color: 'default', label: '草稿' },
  INVITING: { color: 'blue', label: '邀请中' },
  COLLECTING: { color: 'processing', label: '收集中' },
  REVIEW: { color: 'orange', label: '审阅中' },
  GENERATING: { color: 'purple', label: '生成中' },
  PUBLISHED: { color: 'success', label: '已发布' },
};

function taskProgress(project) {
  const tasks = project?.tasks || [];
  const done = tasks.filter(t => t.status === 'COMPLETED' || t.status === 'REVIEWED').length;
  return { done, total: tasks.length || 4 };
}

/**
 * 教师端 · 学段协作课堂工作台列表（设计文档 §5）
 */
export default function TeacherCollabClassrooms() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [courseOptions, setCourseOptions] = useState([]);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    collabApi.listMine()
      .then(res => setProjects(res || []))
      .catch(() => message.error('加载协作项目失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);
  useEffect(() => {
    courseAPI.list({ publishedOnly: true }).then(res => {
      const items = res?.data?.items || res?.data || [];
      setCourseOptions(items.map(c => ({ label: `${c.name}（${c.courseCode}）`, value: c.id })));
    }).catch(() => setCourseOptions([]));
  }, []);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      const res = await collabApi.create({
        title: values.title,
        description: values.description,
        courseId: values.courseId,
        difficulty: values.difficulty || 3,
      });
      message.success('协作项目创建成功，开始邀请各学段学生');
      setModalOpen(false);
      form.resetFields();
      navigate(`/teacher/collab-classrooms/${res?.id || ''}`);
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err?.message || '创建失败');
    } finally {
      setCreating(false);
    }
  };

  const columns = [
    { title: '项目', dataIndex: 'title', key: 'title', render: (t, r) => (
      <Space direction="vertical" size={0}>
        <Text strong>{t}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>{r.description || '—'}</Text>
      </Space>
    )},
    { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (s) => {
      const meta = STATUS_META[s] || { color: 'default', label: s };
      return <Tag color={meta.color}>{meta.label}</Tag>;
    }},
    { title: '协作进度', key: 'progress', width: 180, render: (_, r) => {
      const { done, total } = taskProgress(r);
      return (
        <Space>
          <Progress percent={Math.round((done / total) * 100)} size="small" style={{ width: 90 }} />
          <Text type="secondary" style={{ fontSize: 12 }}>{done}/{total} 角色</Text>
        </Space>
      );
    }},
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150,
      render: (t) => t ? new Date(t).toLocaleDateString('zh-CN') : '-' },
    { title: '操作', key: 'action', width: 130, render: (_, r) => (
      <Button type="primary" size="small" icon={<RightOutlined />}
        onClick={() => navigate(`/teacher/collab-classrooms/${r.id}`)}>
        {r.status === 'PUBLISHED' ? '查看课堂' : '进入工作台'}
      </Button>
    )},
  ];

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            <TeamOutlined /> 学段协作课堂
          </Title>
          <Text type="secondary">
            邀请 小学选故事 · 初中设计角色 · 高中创作台词 · 大学映射法律知识，审阅后由 AI 生成课堂
          </Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          新建协作项目
        </Button>
      </div>

      <Card size="small">
        {loading ? <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
          : projects.length === 0 ? (
            <Empty description="还没有协作项目，点击右上角「新建协作项目」开始">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新建协作项目</Button>
            </Empty>
          ) : (
            <Table rowKey="id" dataSource={projects} columns={columns} pagination={false} size="middle" />
          )}
      </Card>

      <Modal title="新建学段协作项目" open={modalOpen} onCancel={() => setModalOpen(false)}
        onOk={handleCreate} confirmLoading={creating} okText="创建并邀请" width={520}>
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item name="title" label="项目标题" rules={[{ required: true, message: '请填写项目标题' }]}>
            <Input placeholder="如：孔融让梨 · 谦让与权利" maxLength={50} />
          </Form.Item>
          <Form.Item name="description" label="项目描述">
            <Input.TextArea rows={2} placeholder="简单描述这个协作课堂的构想" maxLength={200} />
          </Form.Item>
          <Form.Item name="courseId" label="关联课程（法律知识来源）">
            <Select placeholder="选择课程" options={courseOptions} allowClear />
          </Form.Item>
          <Form.Item name="difficulty" label="课堂难度" initialValue={3}>
            <Select options={[1, 2, 3, 4, 5].map(v => ({ value: v, label: `${'★'.repeat(v)}` }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

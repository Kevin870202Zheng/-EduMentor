import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  Card, Typography, Button, Table, Tag, Space, Modal, Form, Input, InputNumber,
  Select, Popconfirm, message, Empty, Spin, Divider, Collapse,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, FileTextOutlined,
  BookOutlined, QuestionCircleOutlined,
} from '@ant-design/icons';
import { knowledgePointAPI, questionManageAPI, courseAPI } from '../services/api';

const { Title, Text } = Typography;
const { TextArea } = Input;

const QUESTION_TYPES = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '填空题', value: 'FILL_BLANK' },
  { label: '简答题', value: 'SHORT_ANSWER' },
  { label: '论述题', value: 'ESSAY' },
];

const DIFFICULTY_OPTIONS = [
  { label: '★☆☆☆☆ 简单', value: 1 },
  { label: '★★☆☆☆ 较易', value: 2 },
  { label: '★★★☆☆ 中等', value: 3 },
  { label: '★★★★☆ 较难', value: 4 },
  { label: '★★★★★ 困难', value: 5 },
];

export default function TeacherCourseContent() {
  const { courseCode } = useParams();
  const [courseInfo, setCourseInfo] = useState(null);
  const [kps, setKps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedKpId, setExpandedKpId] = useState(null);
  const [questions, setQuestions] = useState({});

  // 知识点编辑弹窗
  const [kpModalOpen, setKpModalOpen] = useState(false);
  const [editingKp, setEditingKp] = useState(null);
  const [kpForm] = Form.useForm();

  // 习题编辑弹窗
  const [qModalOpen, setQModalOpen] = useState(false);
  const [editingQ, setEditingQ] = useState(null);
  const [qKpId, setQKpId] = useState(null);
  const [qForm] = Form.useForm();

  useEffect(() => {
    loadData();
  }, [courseCode]);

  const loadData = async () => {
    setLoading(true);
    try {
      const infoRes = await courseAPI.getByCode(courseCode);
      const course = infoRes?.data || infoRes;
      setCourseInfo(course);
      if (course?.id) {
        const kpRes = await knowledgePointAPI.listByCourse(course.id);
        setKps(kpRes?.data || kpRes || []);
      }
    } catch (err) {
      message.error('加载课程内容失败');
    }
    setLoading(false);
  };

  const loadQuestions = async (kpId) => {
    try {
      const res = await questionManageAPI.listByKp(kpId);
      setQuestions(prev => ({ ...prev, [kpId]: res?.data || res || [] }));
    } catch (err) {
      setQuestions(prev => ({ ...prev, [kpId]: [] }));
    }
  };

  const handleExpandKp = (kpId) => {
    if (expandedKpId === kpId) {
      setExpandedKpId(null);
    } else {
      setExpandedKpId(kpId);
      if (!questions[kpId]) loadQuestions(kpId);
    }
  };

  // ========== 知识点 CRUD ==========

  const openCreateKp = () => {
    setEditingKp(null);
    kpForm.resetFields();
    if (courseInfo?.id) kpForm.setFieldsValue({ courseId: courseInfo.id, difficulty: 3 });
    setKpModalOpen(true);
  };

  const openEditKp = (kp) => {
    setEditingKp(kp);
    kpForm.setFieldsValue(kp);
    setKpModalOpen(true);
  };

  const handleKpSave = async () => {
    try {
      const values = await kpForm.validateFields();
      if (editingKp) {
        await knowledgePointAPI.update(editingKp.id, values);
        message.success('知识点已更新');
      } else {
        await knowledgePointAPI.create(values);
        message.success('知识点已创建');
      }
      setKpModalOpen(false);
      loadData();
    } catch (err) {
      if (err.errorFields) return; // validation error
      message.error('保存失败');
    }
  };

  const handleDeleteKp = async (id) => {
    try {
      await knowledgePointAPI.delete(id);
      message.success('知识点已删除');
      loadData();
    } catch (err) {
      message.error('删除失败');
    }
  };

  // ========== 习题 CRUD ==========

  const openCreateQ = (kpId) => {
    setEditingQ(null);
    setQKpId(kpId);
    qForm.resetFields();
    if (courseInfo?.id) qForm.setFieldsValue({ courseId: courseInfo.id, knowledgePointId: kpId, questionType: 'SINGLE_CHOICE', difficulty: 3 });
    setQModalOpen(true);
  };

  const openEditQ = (q, kpId) => {
    setEditingQ(q);
    setQKpId(kpId);
    const formVals = { ...q, options: q.options ? serializeOptions(q.options) : undefined };
    qForm.setFieldsValue(formVals);
    setQModalOpen(true);
  };

  const serializeOptions = (options) => {
    if (!options) return '';
    if (typeof options === 'string') {
      try { options = JSON.parse(options); } catch { return options; }
    }
    if (Array.isArray(options)) return options.map(o => o.label ? `${o.label}. ${o.text}` : o).join('\n');
    if (typeof options === 'object') return Object.entries(options).map(([k, v]) => `${k}. ${v}`).join('\n');
    return '';
  };

  const handleQSave = async () => {
    try {
      const values = await qForm.validateFields();
      // 解析选项文本为JSON
      if (values.options && typeof values.options === 'string') {
        const lines = values.options.split('\n').filter(l => l.trim());
        const optMap = {};
        lines.forEach(line => {
          const m = line.match(/^([A-Za-z])[.）)\s]\s*(.*)/);
          if (m) optMap[m[1].toUpperCase()] = m[2].trim();
          else optMap[String.fromCharCode(65 + lines.indexOf(line))] = line.trim();
        });
        values.options = Object.keys(optMap).length > 0 ? optMap : undefined;
      }

      if (editingQ) {
        await questionManageAPI.update(editingQ.id, values);
        message.success('习题已更新');
      } else {
        await questionManageAPI.create(values);
        message.success('习题已创建');
      }
      setQModalOpen(false);
      if (qKpId) loadQuestions(qKpId);
    } catch (err) {
      if (err.errorFields) return;
      message.error('保存失败');
    }
  };

  const handleDeleteQ = async (id, kpId) => {
    try {
      await questionManageAPI.delete(id);
      message.success('习题已删除');
      if (kpId) loadQuestions(kpId);
    } catch (err) {
      message.error('删除失败');
    }
  };

  // ========== 渲染 ==========

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  const kpColumns = [
    {
      title: '#', dataIndex: 'orderIndex', key: 'orderIndex', width: 50,
      render: (v) => <Text type="secondary">{(v ?? 0) + 1}</Text>,
    },
    {
      title: '知识点名称', dataIndex: 'name', key: 'name',
      render: (text, record) => (
        <a onClick={() => handleExpandKp(record.id)}>
          <BookOutlined style={{ marginRight: 6 }} />
          {text || '未命名'}
        </a>
      ),
    },
    {
      title: '描述', dataIndex: 'description', key: 'description', width: 300,
      render: (t) => <Text type="secondary" ellipsis={{ tooltip: t }}>{t || '-'}</Text>,
    },
    {
      title: '难度', dataIndex: 'difficulty', key: 'difficulty', width: 100,
      render: (v) => <Tag>{'★'.repeat(v || 3)}</Tag>,
    },
    {
      title: '内容长度', dataIndex: 'content', key: 'contentLen', width: 80,
      render: (v) => <Text type="secondary">{v ? v.length : 0}字</Text>,
    },
    {
      title: '操作', key: 'action', width: 180,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditKp(record)}>编辑</Button>
          <Popconfirm title="确定删除此知识点？关联的习题也将一同删除。" onConfirm={() => handleDeleteKp(record.id)} okText="确定" cancelText="取消">
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card title="📝 课程内容管理" extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateKp}>新增知识点</Button>
      }>
        <Table
          dataSource={kps}
          columns={kpColumns}
          rowKey="id"
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 个知识点` }}
          size="small"
          expandable={{
            expandedRowKeys: expandedKpId ? [expandedKpId] : [],
            onExpand: (expanded, record) => handleExpandKp(record.id),
            expandedRowRender: (record) => {
              const qs = questions[record.id] || [];
              return (
                <div style={{ padding: '8px 0' }}>
                  <div style={{ marginBottom: 8 }}>
                    <Text strong>习题列表（{qs.length} 题）</Text>
                    <Button type="primary" size="small" icon={<PlusOutlined />} style={{ marginLeft: 12 }}
                      onClick={() => openCreateQ(record.id)}>新增习题</Button>
                  </div>
                  {qs.length === 0 ? (
                    <Empty description="暂无习题" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    qs.map((q, idx) => (
                      <div key={q.id} style={{
                        padding: '8px 12px', marginBottom: 6, background: '#fafafa',
                        borderRadius: 6, border: '1px solid #f0f0f0',
                      }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Space>
                            <Tag color="blue">{(QUESTION_TYPES.find(t => t.value === q.questionType) || {}).label || q.questionType}</Tag>
                            <Text>{idx + 1}. {q.content}</Text>
                            <Tag>{'★'.repeat(q.difficulty || 3)}</Tag>
                          </Space>
                          <Space>
                            <Button type="link" size="small" icon={<EditOutlined />}
                              onClick={() => openEditQ(q, record.id)}>编辑</Button>
                            <Popconfirm title="确定删除此题？" onConfirm={() => handleDeleteQ(q.id, record.id)} okText="确定" cancelText="取消">
                              <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
                            </Popconfirm>
                          </Space>
                        </div>
                        {q.options && (
                          <div style={{ marginTop: 4, marginLeft: 24, fontSize: 12, color: '#666' }}>
                            {renderOptions(q.options)}
                          </div>
                        )}
                        <div style={{ marginTop: 2, marginLeft: 24, fontSize: 12 }}>
                          <Text type="success">答案：{q.correctAnswer}</Text>
                          {q.explanation && <Text type="secondary" style={{ marginLeft: 12 }}>解析：{q.explanation}</Text>}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              );
            },
          }}
        />
      </Card>

      {/* 知识点编辑弹窗 */}
      <Modal title={editingKp ? '编辑知识点' : '新增知识点'} open={kpModalOpen}
        onOk={handleKpSave} onCancel={() => setKpModalOpen(false)} width={640} okText="保存" cancelText="取消">
        <Form form={kpForm} layout="vertical">
          <Form.Item name="courseId" hidden><Input /></Form.Item>
          <Form.Item name="name" label="知识点名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：法的概念" />
          </Form.Item>
          <Form.Item name="description" label="简要描述" rules={[{ required: true, message: '请输入描述' }]}>
            <TextArea rows={2} placeholder="一句话概述该知识点" />
          </Form.Item>
          <Form.Item name="content" label="详细内容" rules={[{ required: true, message: '请输入详细内容' }]}>
            <TextArea rows={6} placeholder="学生可直接据此学习的核心知识要点" />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="difficulty" label="难度" rules={[{ required: true }]}>
              <Select options={DIFFICULTY_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="orderIndex" label="排序序号">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 习题编辑弹窗 */}
      <Modal title={editingQ ? '编辑习题' : '新增习题'} open={qModalOpen}
        onOk={handleQSave} onCancel={() => setQModalOpen(false)} width={640} okText="保存" cancelText="取消">
        <Form form={qForm} layout="vertical">
          <Form.Item name="courseId" hidden><Input /></Form.Item>
          <Form.Item name="knowledgePointId" hidden><Input /></Form.Item>
          <Form.Item name="questionType" label="题目类型" rules={[{ required: true }]}>
            <Select options={QUESTION_TYPES} />
          </Form.Item>
          <Form.Item name="content" label="题目内容" rules={[{ required: true, message: '请输入题目' }]}>
            <TextArea rows={3} placeholder="请输入题目内容" />
          </Form.Item>
          <Form.Item name="options" label="选项（每行一个，格式：A. 选项内容）"
            tooltip="仅选择题需要。每行一个选项，如：A. 选项一↵B. 选项二↵C. 选项三↵D. 选项四">
            <TextArea rows={4} placeholder={"A. 选项一\nB. 选项二\nC. 选项三\nD. 选项四"} />
          </Form.Item>
          <Form.Item name="correctAnswer" label="正确答案" rules={[{ required: true, message: '请输入正确答案' }]}>
            <Input placeholder="如：A（选择题）、正确（判断题）、关键内容（简答题）" />
          </Form.Item>
          <Form.Item name="explanation" label="答案解析">
            <TextArea rows={3} placeholder="题目解析（选填）" />
          </Form.Item>
          <Form.Item name="difficulty" label="难度">
            <Select options={DIFFICULTY_OPTIONS} style={{ width: 200 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function renderOptions(options) {
  if (!options) return null;
  let parsed = options;
  if (typeof parsed === 'string') {
    try { parsed = JSON.parse(parsed); } catch { return <Text>{parsed}</Text>; }
  }
  if (Array.isArray(parsed)) {
    return parsed.map((o, i) => {
      const label = typeof o === 'object' ? o.label : String.fromCharCode(65 + i);
      const text = typeof o === 'object' ? o.text : o;
      return <div key={i}><Text>{label}. {text}</Text></div>;
    });
  }
  if (typeof parsed === 'object') {
    return Object.entries(parsed).map(([k, v]) => (
      <div key={k}><Text>{k}. {v}</Text></div>
    ));
  }
  return null;
}

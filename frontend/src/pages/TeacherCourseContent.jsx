import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  Card, Typography, Button, Tag, Space, Modal, Form, Input, InputNumber,
  Select, Popconfirm, message, Empty, Spin, Divider, Tree,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, FileTextOutlined,
  BookOutlined, QuestionCircleOutlined, RobotOutlined,
  ApartmentOutlined, FolderOutlined, FolderOpenOutlined,
  FileOutlined,
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

const DEFAULT_AI_COUNTS = {
  SINGLE_CHOICE: 2,
  MULTIPLE_CHOICE: 1,
  TRUE_FALSE: 1,
  FILL_BLANK: 0,
  SHORT_ANSWER: 0,
};

// 节点类型配置
const NODE_TYPE_CONFIG = {
  VOLUME: { label: '编', color: 'purple', icon: <BookOutlined /> },
  PART: { label: '卷', color: 'geekblue', icon: <FolderOpenOutlined /> },
  CHAPTER: { label: '章', color: 'blue', icon: <FolderOutlined /> },
  SECTION: { label: '节', color: 'cyan', icon: <FileOutlined /> },
  LEAF: { label: '知识点', color: 'green', icon: <FileTextOutlined /> },
};

// 将扁平树节点列表转为 Ant Design Tree 的 data 结构（含层级编号）
const buildTreeData = (nodes, questionsMap) => {
  if (!nodes || nodes.length === 0) return [];

  // 按 parentKpId 分组
  const childrenMap = {};
  nodes.forEach(n => {
    const pid = n.knowledgePoint.parentKpId || 'root';
    if (!childrenMap[pid]) childrenMap[pid] = [];
    childrenMap[pid].push(n);
  });

  // 递归构建
  const buildChildren = (parentId, parentPath) => {
    const items = childrenMap[parentId] || [];
    return items
      .sort((a, b) => (a.knowledgePoint.orderIndex || 0) - (b.knowledgePoint.orderIndex || 0))
      .map((item, idx) => {
        const kp = item.knowledgePoint;
        const typeConfig = NODE_TYPE_CONFIG[kp.type] || NODE_TYPE_CONFIG.LEAF;
        const path = parentPath ? `${parentPath}.${idx + 1}` : `${idx + 1}`;
        const qs = questionsMap?.[kp.id] || [];
        return {
          key: kp.id,
          title: (
            <span>
              <span style={{ color: '#999', fontSize: 11, marginRight: 4 }}>{path}</span>
              {typeConfig.icon}
              <span style={{ marginLeft: 4, fontSize: 13 }}>{kp.name}</span>
              <Tag style={{ marginLeft: 4, fontSize: 10, lineHeight: '16px' }}
                color={typeConfig.color}>
                {typeConfig.label}
              </Tag>
              {qs.length > 0 && kp.type === 'LEAF' && (
                <Text type="secondary" style={{ fontSize: 10 }}>({qs.length}题)</Text>
              )}
            </span>
          ),
          type: kp.type || 'LEAF',
          raw: item,
          isLeaf: !item.hasChild,
          icon: typeConfig.icon,
          children: buildChildren(kp.id, path),
        };
      });
  };

  return buildChildren('root', '');
};

/**
 * 渲染选项文本（用于习题列表展示）
 */
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

export default function TeacherCourseContent() {
  const { courseCode } = useParams();
  const [courseInfo, setCourseInfo] = useState(null);
  const [kps, setKps] = useState([]);       // 扁平列表
  const [treeNodes, setTreeNodes] = useState([]); // 树形数据（扁平带层级）
  const [loading, setLoading] = useState(true);
  const [expandedKpId, setExpandedKpId] = useState(null);
  const [questions, setQuestions] = useState({});
  const [selectedKp, setSelectedKp] = useState(null); // 当前选中的知识点

  // 知识点编辑弹窗
  const [kpModalOpen, setKpModalOpen] = useState(false);
  const [editingKp, setEditingKp] = useState(null);
  const [kpForm] = Form.useForm();

  // 习题编辑弹窗
  const [qModalOpen, setQModalOpen] = useState(false);
  const [editingQ, setEditingQ] = useState(null);
  const [qKpId, setQKpId] = useState(null);
  const [qForm] = Form.useForm();

  // AI 出题弹窗
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [aiKpId, setAiKpId] = useState(null);
  const [aiGenerating, setAiGenerating] = useState(false);
  const [aiForm] = Form.useForm();

  // AI 生成树结构弹窗
  const [treeGenModalOpen, setTreeGenModalOpen] = useState(false);
  const [treeGenLoading, setTreeGenLoading] = useState(false);
  const [treeGenResult, setTreeGenResult] = useState(null);
  const [granularity, setGranularity] = useState('STANDARD');

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
        // 加载知识点列表和树结构
        const [kpRes, treeRes] = await Promise.all([
          knowledgePointAPI.listByCourse(course.id),
          knowledgePointAPI.getTree(course.id),
        ]);
        const flatKps = kpRes?.data || kpRes || [];
        const treeData = treeRes?.data || treeRes || [];
        setKps(flatKps);
        setTreeNodes(treeData);
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

  // ========== Tree 选择 ==========

  const handleTreeSelect = (selectedKeys, info) => {
    if (selectedKeys.length > 0) {
      const kpId = selectedKeys[0];
      const kp = kps.find(k => k.id === kpId);
      setSelectedKp(kp || null);
      setExpandedKpId(kpId);
      if (!questions[kpId]) loadQuestions(kpId);
    } else {
      setSelectedKp(null);
      setExpandedKpId(null);
    }
  };

  // ========== 知识点 CRUD ==========

  const openCreateKp = () => {
    setEditingKp(null);
    kpForm.resetFields();
    if (courseInfo?.id) {
      kpForm.setFieldsValue({
        courseId: courseInfo.id,
        difficulty: 3,
        importance: 3,
        subject: courseInfo.courseCode,
        type: 'LEAF',
        parentKpId: selectedKp?.id || null,
      });
    }
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
      if (err.errorFields) return;
      message.error('保存失败');
    }
  };

  const handleDeleteKp = async (id) => {
    try {
      await knowledgePointAPI.delete(id);
      message.success('知识点已删除');
      if (selectedKp?.id === id) setSelectedKp(null);
      loadData();
    } catch (err) {
      message.error('删除失败: ' + (err.response?.data?.message || err.message));
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
      if (values.options && typeof values.options === 'string') {
        const lines = values.options.split('\n').filter(l => l.trim());
        const optMap = {};
        lines.forEach(line => {
          const m = line.match(/^([A-Za-z])[.）)\s]\s*(.*)/);
          if (m) optMap[m[1].toUpperCase()] = m[2].trim();
          else optMap[String.fromCharCode(65 + lines.indexOf(line))] = line.trim();
        });
        values.options = Object.keys(optMap).length > 0 ? JSON.stringify(optMap) : undefined;
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

  // ========== AI 出题 ==========

  const openAiGenerate = (kpId) => {
    setAiKpId(kpId);
    aiForm.resetFields();
    aiForm.setFieldsValue({ ...DEFAULT_AI_COUNTS });
    setAiModalOpen(true);
  };

  const handleAiGenerate = async () => {
    if (!aiKpId || !courseInfo?.id) return;
    const values = await aiForm.validateFields();

    const counts = {};
    let total = 0;
    Object.entries(values).forEach(([key, val]) => {
      if (val > 0) { counts[key] = val; total += val; }
    });
    if (total === 0) {
      message.warning('请至少选择一道题');
      return;
    }

    setAiGenerating(true);
    try {
      const res = await questionManageAPI.generate({
        courseId: courseInfo.id,
        knowledgePointId: aiKpId,
        counts,
      });
      const data = res?.data || res;
      const generated = data?.generated || 0;
      message.success(`AI 成功生成 ${generated} 道习题`);
      setAiModalOpen(false);
      if (aiKpId) loadQuestions(aiKpId);
    } catch (err) {
      message.error('AI 出题失败: ' + (err.response?.data?.message || err.message || '未知错误'));
    }
    setAiGenerating(false);
  };

  // ========== AI 生成树结构 ==========

  const openTreeGenerate = () => {
    setGranularity('STANDARD');
    setTreeGenResult(null);
    setTreeGenModalOpen(true);
  };

  const handleTreeGenerate = async () => {
    if (!courseInfo?.id) return;
    setTreeGenLoading(true);
    setTreeGenResult(null);
    try {
      const res = await knowledgePointAPI.generateTree(courseInfo.id, { granularity });
      const data = res?.data || res;
      setTreeGenResult(data);
      message.success('树结构生成成功');
      setTreeGenModalOpen(false);
      // 重新加载数据
      await loadData();
    } catch (err) {
      message.error('生成失败: ' + (err.response?.data?.message || err.message || '未知错误'));
    }
    setTreeGenLoading(false);
  };

  // ========== Tree 拖拽 ==========

  const handleTreeDrop = async (info) => {
    const dropKey = info.node.key;
    const dragKey = info.dragNode.key;
    const dropToGap = info.dropToGap;  // false: 拖入内部, true: 拖到间隙
    const dropPos = info.dropPosition; // -1: 之前, 1: 之后

    if (dragKey === dropKey) return;

    let newParentKpId = null;
    let newOrder = 0;

    if (!dropToGap) {
      // 拖入目标节点内部 → 成为其子节点
      newParentKpId = dropKey;
      // 计算目标节点的现有子节点数
      const siblings = treeNodes.filter(n =>
        n.knowledgePoint.parentKpId === dropKey
      );
      newOrder = siblings.length; // 排在最后
    } else {
      // 拖到目标节点之前或之后 → 成为其同级节点
      const dropNode = treeNodes.find(n => n.knowledgePoint.id === dropKey);
      newParentKpId = dropNode?.knowledgePoint.parentKpId || null;

      // 获取该层级的所有兄弟节点，按 orderIndex 排序
      const siblings = treeNodes
        .filter(n => n.knowledgePoint.parentKpId === newParentKpId)
        .sort((a, b) => (a.knowledgePoint.orderIndex || 0) - (b.knowledgePoint.orderIndex || 0));

      if (dropPos < 0) {
        // 放在目标节点之前
        const idx = siblings.findIndex(n => n.knowledgePoint.id === dropKey);
        newOrder = idx >= 0 ? Math.max(0, (siblings[idx]?.knowledgePoint.orderIndex || 0) - 1) : 0;
      } else {
        // 放在目标节点之后
        const idx = siblings.findIndex(n => n.knowledgePoint.id === dropKey);
        newOrder = idx >= 0 ? (siblings[idx]?.knowledgePoint.orderIndex || 0) + 1 : siblings.length;
      }
    }

    try {
      await knowledgePointAPI.moveNode(dragKey, { parentKpId: newParentKpId, orderIndex: newOrder });
      message.success('节点已移动');
      await loadData();
    } catch (err) {
      message.error('移动失败: ' + (err.response?.data?.message || err.message));
    }
  };

  // ========== 渲染 ==========

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  // 将 treeNodes 转为 Tree 组件的 treeData（传入 questions 用于显示习题数）
  const treeData = buildTreeData(treeNodes, questions);

  // 选中的知识点的习题列表
  const selectedQuestions = selectedKp ? (questions[selectedKp.id] || []) : [];

  return (
    <div>
      {/* 顶部标题栏 */}
      <Card title="📝 课程内容管理" extra={
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateKp}>新增知识点</Button>
          <Button icon={<ApartmentOutlined />} onClick={openTreeGenerate}
            loading={treeGenLoading}>🔄 生成树结构</Button>
        </Space>
      }>
        <div style={{ display: 'flex', gap: 20 }}>
          {/* 左侧：树形知识点导航 */}
          <div style={{ width: 380, flexShrink: 0 }}>
            <Card size="small" title="📖 知识点结构" bodyStyle={{ padding: '8px 0', maxHeight: '70vh', overflow: 'auto' }}>
              {treeData.length === 0 ? (
                <Empty description="暂无知识点，请先上传课程资料并发布" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Tree
                  treeData={treeData}
                  defaultExpandedKeys={treeData.slice(0, 3).map(n => n.key)}
                  showIcon={false}
                  draggable
                  onSelect={handleTreeSelect}
                  onDrop={handleTreeDrop}
                  selectedKeys={selectedKp ? [selectedKp.id] : []}
                  style={{ padding: '0 8px', fontSize: 13 }}
                />
              )}
              <Divider style={{ margin: '8px 0' }} />
              <Text type="secondary" style={{ display: 'block', textAlign: 'center', fontSize: 12, padding: 4 }}>
                💡 可以拖拽节点调整结构，选中节点后管理习题
              </Text>
            </Card>
          </div>

          {/* 右侧：选中知识点的习题和操作 */}
          <div style={{ flex: 1, minWidth: 0 }}>
            {selectedKp ? (
              <div>
                {/* 知识点详情和操作 */}
                <Card size="small" style={{ marginBottom: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <Space>
                        <Tag color={NODE_TYPE_CONFIG[selectedKp.type]?.color}>
                          {NODE_TYPE_CONFIG[selectedKp.type]?.label || '知识点'}
                        </Tag>
                        <Text strong style={{ fontSize: 15 }}>{selectedKp.name}</Text>
                        <Tag>{'★'.repeat(selectedKp.difficulty || 3)}</Tag>
                      </Space>
                      <div style={{ marginTop: 4 }}>
                        <Text type="secondary">{selectedKp.description || '-'}</Text>
                      </div>
                    </div>
                    <Space>
                      <Button type="link" size="small" icon={<EditOutlined />}
                        onClick={() => openEditKp(selectedKp)}>编辑</Button>
                      <Popconfirm title="确定删除此知识点？"
                        onConfirm={() => handleDeleteKp(selectedKp.id)}
                        okText="确定" cancelText="取消">
                        <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
                      </Popconfirm>
                      <Button size="small" icon={<PlusOutlined />}
                        onClick={() => openCreateQ(selectedKp.id)}>新增习题</Button>
                      <Button size="small" icon={<RobotOutlined />}
                        onClick={() => openAiGenerate(selectedKp.id)}>AI 出题</Button>
                    </Space>
                  </div>
                </Card>

                {/* 习题列表 */}
                <Card size="small" title={`📝 习题列表（${selectedQuestions.length} 题）`}>
                  {selectedQuestions.length === 0 ? (
                    <Empty description="该知识点暂无习题" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  ) : (
                    selectedQuestions.map((q, idx) => (
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
                              onClick={() => openEditQ(q, selectedKp.id)}>编辑</Button>
                            <Popconfirm title="确定删除此题？"
                              onConfirm={() => handleDeleteQ(q.id, selectedKp.id)}
                              okText="确定" cancelText="取消">
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
                </Card>
              </div>
            ) : (
              <Card>
                <Empty description="请在左侧树结构中选择一个知识点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              </Card>
            )}
          </div>
        </div>
      </Card>

      {/* 知识点编辑弹窗 */}
      <Modal title={editingKp ? '编辑知识点' : '新增知识点'} open={kpModalOpen}
        onOk={handleKpSave} onCancel={() => setKpModalOpen(false)} width={640} okText="保存" cancelText="取消">
        <Form form={kpForm} layout="vertical">
          <Form.Item name="courseId" hidden><Input /></Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：法的概念" />
          </Form.Item>
          <Form.Item name="description" label="简要描述" rules={[{ required: true, message: '请输入描述' }]}>
            <TextArea rows={2} placeholder="一句话概述该知识点" />
          </Form.Item>
          <Form.Item name="content" label="详细内容" rules={[{ required: true, message: '请输入详细内容' }]}>
            <TextArea rows={6} placeholder="学生可直接据此学习的核心知识要点" />
          </Form.Item>
          <Space style={{ width: '100%' }}>
            <Form.Item name="type" label="节点类型">
              <Select style={{ width: 140 }}>
                <Select.Option value="VOLUME">编 (Volume)</Select.Option>
                <Select.Option value="PART">卷 (Part)</Select.Option>
                <Select.Option value="CHAPTER">章 (Chapter)</Select.Option>
                <Select.Option value="SECTION">节 (Section)</Select.Option>
                <Select.Option value="LEAF">知识点 (Leaf)</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name="parentKpId" label="父节点">
              <Select style={{ width: 200 }} allowClear placeholder="根节点（不选）"
                showSearch filterOption={(input, option) =>
                  option.children?.includes(input) || false}>
                {kps.filter(k => k.id !== editingKp?.id).map(kp => (
                  <Select.Option key={kp.id} value={kp.id}>
                    {kp.name}
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }}>
            <Form.Item name="difficulty" label="难度" rules={[{ required: true }]}>
              <Select options={DIFFICULTY_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="importance" label="重要程度" rules={[{ required: true }]}>
              <Select options={DIFFICULTY_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="orderIndex" label="排序序号">
              <InputNumber min={0} style={{ width: 100 }} />
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

      {/* AI 出题弹窗 */}
      <Modal title="🤖 AI 智能出题" open={aiModalOpen}
        onOk={handleAiGenerate} onCancel={() => setAiModalOpen(false)}
        width={480} okText="🤖 生成" cancelText="取消"
        confirmLoading={aiGenerating}>
        <Form form={aiForm} layout="vertical">
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            AI 将根据该知识点的课程内容智能生成习题，自动匹配难度。
          </Text>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 24px' }}>
            <Form.Item name="SINGLE_CHOICE" label="单选题">
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="MULTIPLE_CHOICE" label="多选题">
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="TRUE_FALSE" label="判断题">
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="FILL_BLANK" label="填空题">
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="SHORT_ANSWER" label="简答题">
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      {/* AI 生成树结构弹窗 */}
      <Modal title="🔄 AI 生成树结构" open={treeGenModalOpen}
        onOk={handleTreeGenerate} onCancel={() => setTreeGenModalOpen(false)}
        width={520} okText="🤖 生成" cancelText="取消"
        confirmLoading={treeGenLoading}>
        <div>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            AI 将根据当前课程的所有知识点，自动生成编→章→节的树状结构。
            已有结构将被参考保留，AI 会把新增知识点放入合适的层级位置。
          </Text>

          <div style={{ background: '#f5f5f5', padding: 12, borderRadius: 8, marginBottom: 16 }}>
            <Text strong>当前课程概览</Text>
            <div style={{ marginTop: 8 }}>
              <Text>知识点总数：<Tag color="blue">{kps.length}</Tag></Text>
              {treeNodes.length > 0 && (
                <Text style={{ marginLeft: 16 }}>
                  已有树节点：<Tag color="green">{treeNodes.length}</Tag>
                </Text>
              )}
            </div>
          </div>

          <div style={{ marginBottom: 16 }}>
            <Text strong>生成粒度</Text>
            <Select value={granularity} onChange={setGranularity} style={{ width: '100%', marginTop: 8 }}>
              <Select.Option value="STANDARD">标准（编 → 章 → 节）</Select.Option>
              <Select.Option value="COMPACT">精简（章 → 节，适合小课程）</Select.Option>
              <Select.Option value="FULL">完整（编 → 卷 → 章 → 节）</Select.Option>
            </Select>
          </div>
        </div>
      </Modal>
    </div>
  );
}

import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Typography, Button, Upload, Table, Tag, Spin, Alert,
  Space, Descriptions, Divider, Modal, message, Empty, Steps,
  Tabs, Select, Popconfirm, Form, Input,
} from 'antd';
import {
  UploadOutlined, RobotOutlined, CheckCircleOutlined,
  FileTextOutlined, ArrowLeftOutlined, SendOutlined,
  UserOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
} from '@ant-design/icons';
import { courseContentAPI, courseTeacherAPI, courseAPI } from '../services/api';
import TeacherCourseContent from './TeacherCourseContent';

const { Title, Text, Paragraph } = Typography;

const STATUS_MAP = {
  pending: { color: 'default', label: '待处理' },
  extracting: { color: 'processing', label: 'AI提取中' },
  extracted: { color: 'success', label: '已提取' },
  published: { color: 'purple', label: '已发布' },
  failed: { color: 'error', label: '失败' },
};

const ROLE_MAP = {
  lecturer: { label: '主讲教师', color: 'blue' },
  tutor: { label: '辅导教师', color: 'green' },
  assistant: { label: '助教', color: 'orange' },
};

// 学段选项（PRD v4.0 §5，课程教育阶段定位）
const STAGE_OPTIONS = [
  { label: '🏫 小学', value: 'PRIMARY' },
  { label: '📖 初中', value: 'JUNIOR' },
  { label: '🟢 高中', value: 'SENIOR' },
  { label: '🎓 大学', value: 'UNIVERSITY' },
];
const STAGE_NAMES = { PRIMARY: '小学', JUNIOR: '初中', SENIOR: '高中', UNIVERSITY: '大学' };
const STAGE_COLORS = { PRIMARY: 'blue', JUNIOR: 'orange', SENIOR: 'green', UNIVERSITY: 'purple' };

export default function TeacherCourseManage() {
  const { courseCode } = useParams();
  const navigate = useNavigate();
  const [courseInfo, setCourseInfo] = useState(null);
  const [materials, setMaterials] = useState([]);
  const [loading, setLoading] = useState(true);
  const [extracting, setExtracting] = useState(null);
  const [extractionResult, setExtractionResult] = useState(null);
  const [resultModalOpen, setResultModalOpen] = useState(false);

  // 教师管理状态
  const [teachers, setTeachers] = useState([]);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [availableTeachers, setAvailableTeachers] = useState([]);
  const [selectedTeacherId, setSelectedTeacherId] = useState(null);
  const [selectedRole, setSelectedRole] = useState('tutor');
  const [assigning, setAssigning] = useState(false);
  const [uploading, setUploading] = useState(0);
  const uploadCount = useRef(0);

  // 课程信息编辑（含学段修改）
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [savingCourse, setSavingCourse] = useState(false);
  const [courseForm] = Form.useForm();

  useEffect(() => {
    loadData();
  }, [courseCode]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [infoRes, matsRes] = await Promise.all([
        courseContentAPI.getCourseInfo(courseCode),
        courseContentAPI.listMaterials(courseCode),
      ]);
      setCourseInfo(infoRes?.data || infoRes);
      setMaterials(matsRes?.data || matsRes || []);
    } catch (err) {
      console.error('Failed to load course data:', err);
      message.error('加载课程信息失败');
    }
    setLoading(false);
  };

  // ========== 资料管理 ==========

  const handleUpload = async (file) => {
    uploadCount.current += 1;
    setUploading(uploadCount.current);
    const idx = uploadCount.current;
    try {
      await courseContentAPI.uploadMaterial(courseCode, file);
      message.success(`[${idx}] ${file.name} 上传成功`);
    } catch (err) {
      message.error(`[${idx}] ${file.name} 上传失败`);
    }
    uploadCount.current -= 1;
    setUploading(uploadCount.current);
    if (uploadCount.current === 0) loadData();
    return false;
  };

  const handleExtract = async (materialId) => {
    setExtracting(materialId);
    try {
      const res = await courseContentAPI.extractMaterial(courseCode, materialId);
      message.success('AI 提取完成');
      loadData();
    } catch (err) {
      message.error('AI 提取失败: ' + (err.message || '未知错误'));
    }
    setExtracting(null);
  };

  const handleViewResult = async (materialId) => {
    try {
      const res = await courseContentAPI.getExtractionResult(courseCode, materialId);
      setExtractionResult(res?.data?.result || res?.result);
      setResultModalOpen(true);
    } catch (err) {
      message.error('获取提取结果失败');
    }
  };

  const handleDeleteMaterial = async (materialId) => {
    try {
      await courseContentAPI.deleteMaterial(courseCode, materialId);
      message.success('资料已删除');
      loadData();
    } catch (err) {
      message.error('删除失败: ' + (err.message || '未知错误'));
    }
  };

  const handlePublish = (materialId) => {
    Modal.confirm({
      title: '确认发布',
      content: '发布后提取结果将被写入课程知识点和习题数据库，确定要发布吗？',
      onOk: async () => {
        try {
          await courseContentAPI.publishExtraction(courseCode, materialId);
          message.success('已发布到课程知识库');
          loadData();
        } catch (err) {
          message.error('发布失败: ' + (err.message || '未知错误'));
        }
      },
    });
  };

  const materialColumns = [
    {
      title: '资料名称', dataIndex: 'title', key: 'title',
      render: (text) => <Text strong><FileTextOutlined style={{ marginRight: 6 }} />{text}</Text>,
    },
    { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 80, render: (t) => <Tag>{t?.toUpperCase() || '未知'}</Tag> },
    { title: '内容长度', dataIndex: 'textLength', key: 'textLength', width: 100, render: (v) => `${(v || 0).toLocaleString()} 字` },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 110,
      render: (s) => { const m = STATUS_MAP[s] || { color: 'default', label: s }; return <Tag color={m.color}>{m.label}</Tag>; },
    },
    {
      title: '操作', key: 'action', width: 320,
      render: (_, record) => (
        <Space>
          {record.status === 'pending' && (
            <Button type="primary" size="small" icon={<RobotOutlined />} loading={extracting === record.id} onClick={() => handleExtract(record.id)}>AI 提取</Button>
          )}
          {record.status === 'extracted' && (
            <>
              <Button size="small" onClick={() => handleViewResult(record.id)}>查看结果</Button>
              <Button type="primary" size="small" icon={<SendOutlined />} onClick={() => handlePublish(record.id)}>发布</Button>
            </>
          )}
          {record.status === 'published' && <Tag icon={<CheckCircleOutlined />} color="success">已发布</Tag>}
          {record.status === 'failed' && <Tag color="error">提取失败</Tag>}
          <Popconfirm title="确定删除该资料？此操作不可恢复。" onConfirm={() => handleDeleteMaterial(record.id)} okText="确定" cancelText="取消">
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ========== 教师管理 ==========

  const loadTeachers = async () => {
    if (!courseInfo?.id) return;
    try {
      const res = await courseTeacherAPI.listTeachers(courseInfo.id);
      setTeachers(res?.data || res || []);
    } catch (err) {
      console.error('Failed to load teachers:', err);
    }
  };

  const loadAvailableTeachers = async () => {
    try {
      const res = await courseTeacherAPI.listAvailable();
      setAvailableTeachers(res?.data || res || []);
    } catch (err) {
      console.error('Failed to load available teachers:', err);
    }
  };

  const openAssignModal = () => {
    loadAvailableTeachers();
    setSelectedTeacherId(null);
    setSelectedRole('tutor');
    setAssignModalOpen(true);
  };

  const handleAssignTeacher = async () => {
    if (!selectedTeacherId || !courseInfo?.id) return;
    setAssigning(true);
    try {
      await courseTeacherAPI.assignTeacher({
        courseId: courseInfo.id,
        teacherId: selectedTeacherId,
        role: selectedRole,
      });
      message.success('教师分配成功');
      setAssignModalOpen(false);
      loadTeachers();
    } catch (err) {
      message.error('分配失败: ' + (err.message || '未知错误'));
    }
    setAssigning(false);
  };

  const handleRemoveTeacher = async (teacherId) => {
    try {
      await courseTeacherAPI.removeTeacher(teacherId);
      message.success('教师已移除');
      loadTeachers();
    } catch (err) {
      message.error('移除失败: ' + (err.message || '未知错误'));
    }
  };

  // ========== 课程信息编辑（含学段修改，PRD v4.0 §19） ==========
  const openEditCourse = () => {
    courseForm.setFieldsValue({
      name: courseInfo?.name,
      description: courseInfo?.description,
      subject: courseInfo?.subject,
      gradeLevel: courseInfo?.gradeLevel,
      stage: courseInfo?.stage,
    });
    setEditModalOpen(true);
  };

  const handleSaveCourse = async (values) => {
    if (!courseInfo?.id) return;
    setSavingCourse(true);
    try {
      await courseAPI.update(courseInfo.id, values);
      message.success('课程信息已更新');
      setEditModalOpen(false);
      await loadData();
    } catch (err) {
      message.error('保存失败: ' + (err.response?.data?.message || err.message || '未知错误'));
    }
    setSavingCourse(false);
  };

  const teacherColumns = [
    {
      title: '教师姓名', dataIndex: 'teacherName', key: 'teacherName', width: 150,
      render: (text) => <Text strong><UserOutlined style={{ marginRight: 6 }} />{text || '未知'}</Text>,
    },
    {
      title: '角色', dataIndex: 'role', key: 'role', width: 120,
      render: (role) => {
        const m = ROLE_MAP[role] || { label: role, color: 'default' };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, record) => (
        <Popconfirm title="确定移除该教师？" onConfirm={() => handleRemoveTeacher(record.id)} okText="确定" cancelText="取消">
          <Button type="link" danger icon={<DeleteOutlined />} size="small">移除</Button>
        </Popconfirm>
      ),
    },
  ];

  // 切换 Tab 时加载教师列表
  const onTabChange = (key) => {
    if (key === 'teachers') {
      loadTeachers();
    }
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  // ========== 课程信息头 ==========

  const courseHeader = courseInfo && (
    <Card style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>📚 {courseInfo.name}</Title>
          <Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
            编号: <Text code>{courseInfo.courseCode}</Text>
            {' · '}学科: {courseInfo.subject}
            {' · '}年级: {courseInfo.gradeLevel || '通用'}
            {' · '}
            {courseInfo.stage ? (
              <Tag color={STAGE_COLORS[courseInfo.stage] || 'blue'}>{STAGE_NAMES[courseInfo.stage] || courseInfo.stage}学段</Tag>
            ) : (
              <Tag color="orange">未设置学段</Tag>
            )}
            {' '}
            {courseInfo.isPublished ? <Tag color="green" size="small">已发布</Tag> : <Tag color="orange" size="small">未发布</Tag>}
          </Text>
        </div>
        <Button icon={<EditOutlined />} onClick={openEditCourse}>编辑课程</Button>
      </div>
      {courseInfo.description && (
        <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>{courseInfo.description}</Paragraph>
      )}
    </Card>
  );

  // ========== Tab: 资料管理 ==========
  const materialsTab = (
    <div>
      <Card title="📤 上传课程资料" style={{ marginBottom: 16 }}>
        <Upload.Dragger multiple accept=".txt,.md,.html,.json,.csv,.pdf,.docx,.doc,.xlsx,.xls,.pptx,.ppt" beforeUpload={handleUpload} showUploadList={false}>
          <p className="ant-upload-drag-icon">{uploading > 0 ? <Spin /> : <UploadOutlined />}</p>
          <p className="ant-upload-text">{uploading > 0 ? `正在上传 ${uploading} 个文件...` : '点击或拖拽文件上传'}</p>
          <p className="ant-upload-hint">支持 .txt / .md / .html / .json / .csv / .pdf / .docx / .xlsx / .pptx，可按住 Cmd 多选文件，或将文件夹拖入上传区</p>
        </Upload.Dragger>
      </Card>

      <Card title="📋 课程资料列表">
        {materials.length === 0 ? (
          <Empty description="暂无资料，请上传课程资料后使用 AI 提取" />
        ) : (
          <Table dataSource={materials} columns={materialColumns} rowKey="id" pagination={false} size="small" />
        )}
      </Card>

      <Card title="💡 使用流程" style={{ marginTop: 16 }}>
        <Steps size="small" current={-1} direction="horizontal" items={[
          { title: '上传资料', description: '上传课程文本资料' },
          { title: 'AI 提取', description: '系统自动提取知识点/关系/习题' },
          { title: '审核修改', description: '确认 AI 提取结果（即将上线）' },
          { title: '发布入库', description: '写入课程知识库并自动向量化' },
        ]} />
      </Card>

      <Modal title="🤖 AI 提取结果" open={resultModalOpen} onCancel={() => setResultModalOpen(false)}
        footer={[<Button key="close" onClick={() => setResultModalOpen(false)}>关闭</Button>]} width={800}>
        {extractionResult ? (
          <pre style={{ background: '#f6f8fa', padding: 16, borderRadius: 8, maxHeight: 500, overflow: 'auto', fontSize: 13, lineHeight: 1.6 }}>
            {typeof extractionResult === 'string' ? JSON.stringify(JSON.parse(extractionResult), null, 2) : JSON.stringify(extractionResult, null, 2)}
          </pre>
        ) : <Empty description="暂无提取结果" />}
      </Modal>
    </div>
  );

  // ========== Tab: 教师管理 ==========
  const teachersTab = (
    <div>
      <Card title="👨‍🏫 当前授课教师" extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openAssignModal}>分配教师</Button>
      }>
        {teachers.length === 0 ? (
          <Empty description="暂未分配教师，请点击「分配教师」添加" />
        ) : (
          <Table dataSource={teachers} columns={teacherColumns} rowKey="id" pagination={false} size="small" />
        )}
      </Card>

      <Modal title="分配教师" open={assignModalOpen} onCancel={() => setAssignModalOpen(false)}
        onOk={handleAssignTeacher} confirmLoading={assigning} okText="确认分配" cancelText="取消">
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <Text strong>选择教师：</Text>
            <Select style={{ width: '100%', marginTop: 4 }}
              placeholder="请选择教师"
              value={selectedTeacherId}
              onChange={setSelectedTeacherId}
              options={availableTeachers.map(t => ({ label: t.displayName, value: t.id }))}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text strong>教师角色：</Text>
            <Select style={{ width: '100%', marginTop: 4 }}
              value={selectedRole}
              onChange={setSelectedRole}
              options={[
                { label: '主讲教师', value: 'lecturer' },
                { label: '辅导教师', value: 'tutor' },
                { label: '助教', value: 'assistant' },
              ]}
            />
          </div>
        </Space>
      </Modal>
    </div>
  );

  return (
    <div>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/teacher/dashboard')} style={{ marginBottom: 12 }}>
        返回驾驶舱
      </Button>

      {courseHeader}

      {/* 课程信息编辑弹窗（含学段修改） */}
      <Modal title="✏️ 编辑课程信息" open={editModalOpen} onCancel={() => setEditModalOpen(false)}
        onOk={() => courseForm.submit()} confirmLoading={savingCourse} okText="保存" cancelText="取消">
        <Form form={courseForm} layout="vertical" onFinish={handleSaveCourse} style={{ marginTop: 16 }}>
          <Form.Item label="课程名称" name="name" rules={[{ required: true, message: '请输入课程名称' }]}>
            <Input placeholder="课程名称" />
          </Form.Item>
          <Form.Item label="学科" name="subject">
            <Select placeholder="请选择学科" allowClear options={[
              { label: '法律', value: '法律' },
              { label: '数学', value: '数学' },
              { label: '计算机', value: '计算机' },
              { label: '物理', value: '物理' },
              { label: '英语', value: '英语' },
              { label: '化学', value: '化学' },
              { label: '生物', value: '生物' },
              { label: '其他', value: '其他' },
            ]} />
          </Form.Item>
          <Form.Item label="学段" name="stage" tooltip="课程教育阶段定位，决定课程出现在哪个学段">
            <Select options={STAGE_OPTIONS} placeholder="选择学段" allowClear />
          </Form.Item>
          <Form.Item label="适用年级" name="gradeLevel">
            <Input placeholder="如：大一" />
          </Form.Item>
          <Form.Item label="课程描述" name="description">
            <Input.TextArea rows={3} placeholder="课程简介" />
          </Form.Item>
        </Form>
      </Modal>

      <Tabs defaultActiveKey="materials" onChange={onTabChange} items={[
        { key: 'materials', label: '📄 资料管理', children: materialsTab },
        { key: 'content', label: '📝 课程内容', children: <TeacherCourseContent /> },
        { key: 'teachers', label: '👨‍🏫 教师管理', children: teachersTab },
      ]} />
    </div>
  );
}

import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Typography, Button, Upload, Table, Tag, Spin, Alert,
  Space, Descriptions, Divider, Modal, message, Empty, Steps,
} from 'antd';
import {
  UploadOutlined, RobotOutlined, CheckCircleOutlined,
  FileTextOutlined, ArrowLeftOutlined, SendOutlined,
} from '@ant-design/icons';
import { courseContentAPI } from '../services/api';

const { Title, Text, Paragraph } = Typography;

const STATUS_MAP = {
  pending: { color: 'default', label: '待处理' },
  extracting: { color: 'processing', label: 'AI提取中' },
  extracted: { color: 'success', label: '已提取' },
  published: { color: 'purple', label: '已发布' },
  failed: { color: 'error', label: '失败' },
};

export default function TeacherCourseManage() {
  const { courseCode } = useParams();
  const navigate = useNavigate();
  const [courseInfo, setCourseInfo] = useState(null);
  const [materials, setMaterials] = useState([]);
  const [loading, setLoading] = useState(true);
  const [extracting, setExtracting] = useState(null);
  const [extractionResult, setExtractionResult] = useState(null);
  const [resultModalOpen, setResultModalOpen] = useState(false);

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

  const handleUpload = async (file) => {
    try {
      const res = await courseContentAPI.uploadMaterial(courseCode, file);
      message.success(`「${file.name}」上传成功`);
      loadData(); // 刷新列表
    } catch (err) {
      message.error('上传失败: ' + (err.message || '未知错误'));
    }
    return false; // 阻止默认上传行为
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

  const columns = [
    {
      title: '资料名称',
      dataIndex: 'title',
      key: 'title',
      render: (text) => <Text strong><FileTextOutlined style={{ marginRight: 6 }} />{text}</Text>,
    },
    {
      title: '类型',
      dataIndex: 'fileType',
      key: 'fileType',
      width: 80,
      render: (t) => <Tag>{t?.toUpperCase() || '未知'}</Tag>,
    },
    {
      title: '内容长度',
      dataIndex: 'textLength',
      key: 'textLength',
      width: 100,
      render: (v) => `${(v || 0).toLocaleString()} 字`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s) => {
        const m = STATUS_MAP[s] || { color: 'default', label: s };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      render: (_, record) => (
        <Space>
          {record.status === 'pending' && (
            <Button
              type="primary"
              size="small"
              icon={<RobotOutlined />}
              loading={extracting === record.id}
              onClick={() => handleExtract(record.id)}
            >
              AI 提取
            </Button>
          )}
          {record.status === 'extracted' && (
            <>
              <Button size="small" onClick={() => handleViewResult(record.id)}>
                查看结果
              </Button>
              <Button
                type="primary"
                size="small"
                icon={<SendOutlined />}
                onClick={() => handlePublish(record.id)}
              >
                发布
              </Button>
            </>
          )}
          {record.status === 'published' && (
            <Tag icon={<CheckCircleOutlined />} color="success">已发布</Tag>
          )}
          {record.status === 'failed' && (
            <Tag color="error">提取失败</Tag>
          )}
        </Space>
      ),
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      {/* 返回按钮 */}
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/teacher/dashboard')}
        style={{ marginBottom: 12 }}
      >
        返回驾驶舱
      </Button>

      {/* 课程信息 */}
      {courseInfo && (
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Title level={4} style={{ margin: 0 }}>
                📚 {courseInfo.name}
              </Title>
              <Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
                编号: <Text code>{courseInfo.courseCode}</Text>
                {' · '}学科: {courseInfo.subject}
                {' · '}年级: {courseInfo.gradeLevel || '通用'}
                {' · '}
                {courseInfo.published ? (
                  <Tag color="green" size="small">已发布</Tag>
                ) : (
                  <Tag size="small">未发布</Tag>
                )}
              </Text>
            </div>
          </div>
          {courseInfo.description && (
            <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
              {courseInfo.description}
            </Paragraph>
          )}
        </Card>
      )}

      {/* 上传区 */}
      <Card title="📤 上传课程资料" style={{ marginBottom: 16 }}>
        <Upload.Dragger
          accept=".txt,.md,.html,.json,.csv"
          beforeUpload={handleUpload}
          showUploadList={false}
        >
          <p className="ant-upload-drag-icon">
            <UploadOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
          <p className="ant-upload-hint">
            支持 .txt / .md / .html / .json / .csv 格式，AI 将从资料中提取知识点和习题
          </p>
        </Upload.Dragger>
      </Card>

      {/* 资料列表 */}
      <Card title="📋 课程资料列表">
        {materials.length === 0 ? (
          <Empty description="暂无资料，请上传课程资料后使用 AI 提取" />
        ) : (
          <Table
            dataSource={materials}
            columns={columns}
            rowKey="id"
            pagination={false}
            size="small"
          />
        )}
      </Card>

      {/* 使用说明 */}
      <Card title="💡 使用流程" style={{ marginTop: 16 }}>
        <Steps
          size="small"
          current={-1}
          direction="horizontal"
          items={[
            { title: '上传资料', description: '上传课程文本资料' },
            { title: 'AI 提取', description: '系统自动提取知识点/关系/习题' },
            { title: '审核修改', description: '确认 AI 提取结果（即将上线）' },
            { title: '发布入库', description: '写入课程知识库并自动向量化' },
          ]}
        />
      </Card>

      {/* 提取结果预览 Modal */}
      <Modal
        title="🤖 AI 提取结果"
        open={resultModalOpen}
        onCancel={() => setResultModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setResultModalOpen(false)}>关闭</Button>,
        ]}
        width={800}
      >
        {extractionResult ? (
          <pre style={{
            background: '#f6f8fa',
            padding: 16,
            borderRadius: 8,
            maxHeight: 500,
            overflow: 'auto',
            fontSize: 13,
            lineHeight: 1.6,
          }}>
            {typeof extractionResult === 'string'
              ? JSON.stringify(JSON.parse(extractionResult), null, 2)
              : JSON.stringify(extractionResult, null, 2)}
          </pre>
        ) : (
          <Empty description="暂无提取结果" />
        )}
      </Modal>
    </div>
  );
}

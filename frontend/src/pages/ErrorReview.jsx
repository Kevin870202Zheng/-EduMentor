import { useState } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Typography, Button, Modal, Input, Rate, Steps, Empty } from 'antd';
import { FileExclamationOutlined, CheckCircleOutlined, SyncOutlined, BulbOutlined } from '@ant-design/icons';
import { errorAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;
const { TextArea } = Input;

const ERROR_TYPE_MAP = {
  knowledge: { color: 'red', label: '知识型错误' },
  method: { color: 'orange', label: '方法型错误' },
  calculation: { color: 'blue', label: '计算型错误' },
  metacognition: { color: 'purple', label: '元认知型错误' },
};

const EBBINGHAUS = ['1天后', '3天后', '7天后', '14天后', '30天后'];

const DEMO_RECORDS = [
  { id: '1', error_type: 'knowledge', error_category: '概念不清', kp_name: '极限定义', is_resolved: false, created_at: '2026-06-15' },
  { id: '2', error_type: 'method', error_category: '思路错误', kp_name: '导数应用', is_resolved: false, created_at: '2026-06-14' },
  { id: '3', error_type: 'calculation', error_category: '粗心计算', kp_name: '不定积分', is_resolved: true, created_at: '2026-06-12' },
  { id: '4', error_type: 'metacognition', error_category: '元认知偏差', kp_name: '函数连续性', is_resolved: false, created_at: '2026-06-10' },
];

export default function ErrorReview() {
  const [records, setRecords] = useState(DEMO_RECORDS);
  const [reviewModal, setReviewModal] = useState(null);
  const [reflection, setReflection] = useState('');
  const [understanding, setUnderstanding] = useState(3);
  const { user } = useAuth();

  const columns = [
    { title: '知识点', dataIndex: 'kp_name', key: 'kp_name' },
    { title: '错误类型', dataIndex: 'error_type', key: 'error_type', render: (t) => <Tag {...ERROR_TYPE_MAP[t] || {}}>{ERROR_TYPE_MAP[t]?.label || t}</Tag> },
    { title: '错误分类', dataIndex: 'error_category', key: 'error_category' },
    { title: '日期', dataIndex: 'created_at', key: 'created_at' },
    { title: '状态', dataIndex: 'is_resolved', key: 'is_resolved', render: (v) => v ? <Tag color="green" icon={<CheckCircleOutlined />}>已解决</Tag> : <Tag color="orange" icon={<SyncOutlined />}>待复盘</Tag> },
    { title: '操作', key: 'action', render: (_, record) => !record.is_resolved && (
      <Button type="link" onClick={() => setReviewModal(record)}>开始复盘</Button>
    )},
  ];

  const typeStats = {
    knowledge: records.filter(r => r.error_type === 'knowledge').length,
    method: records.filter(r => r.error_type === 'method').length,
    calculation: records.filter(r => r.error_type === 'calculation').length,
    metacognition: records.filter(r => r.error_type === 'metacognition').length,
  };

  const handleSubmitReview = async () => {
    if (!reflection.trim()) return;

    try {
      await errorAPI.submitReview({
        user_id: user?.id,
        record_id: reviewModal.id,
        reflection,
        understanding,
      });
    } catch (e) {
      // 离线模式继续
    }

    setRecords(prev => prev.map(r => r.id === reviewModal.id ? { ...r, is_resolved: true } : r));
    setReviewModal(null);
    setReflection('');
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>📝 智能错题复盘与反思</Title>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card><Statistic title="总错题" value={records.length} prefix={<FileExclamationOutlined />} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="已解决" value={records.filter(r => r.is_resolved).length} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="待复盘" value={records.filter(r => !r.is_resolved).length} prefix={<SyncOutlined spin />} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="待复习" value="5" prefix={<BulbOutlined />} valueStyle={{ color: '#1677ff' }} /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <Card title="📊 错误类型分布" size="small">
            <Row gutter={8}>
              {Object.entries(typeStats).map(([key, val]) => (
                <Col span={6} key={key}>
                  <div style={{ textAlign: 'center', padding: 8 }}>
                    <Tag {...ERROR_TYPE_MAP[key]} style={{ fontSize: 14 }}>{val}题</Tag>
                    <div><Text type="secondary" style={{ fontSize: 12 }}>{ERROR_TYPE_MAP[key]?.label}</Text></div>
                  </div>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="⏰ 间隔复习计划（艾宾浩斯）" size="small">
            <Steps size="small" current={0} items={EBBINGHAUS.map((d, i) => ({ title: d, status: i === 0 ? 'process' : 'wait' }))} />
          </Card>
        </Col>
      </Row>

      <Card title="错题列表">
        <Table dataSource={records} columns={columns} rowKey="id" pagination={false} size="small" />
      </Card>

      <Modal
        title={`🧠 错题复盘 - ${reviewModal?.kp_name || ''}`}
        open={!!reviewModal}
        onCancel={() => setReviewModal(null)}
        onOk={handleSubmitReview}
        okText="提交复盘"
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>错误类型：</Text>
          <Tag {...ERROR_TYPE_MAP[reviewModal?.error_type] || {}}>{ERROR_TYPE_MAP[reviewModal?.error_type]?.label}</Tag>
        </div>

        <div style={{ marginBottom: 12 }}>
          <Text strong>自我反思：</Text>
          <TextArea
            value={reflection}
            onChange={e => setReflection(e.target.value)}
            placeholder="这道题为什么错了？我的思路哪里出了问题？下次如何避免？"
            rows={4}
          />
        </div>

        <div style={{ marginBottom: 12 }}>
          <Text strong>理解程度：</Text>
          <Rate value={understanding} onChange={setUnderstanding} />
        </div>

        <div style={{ padding: 12, background: '#f6ffed', borderRadius: 6 }}>
          <Text type="secondary">💡 反思引导：想想这道题的核心方法还能用在哪些场景？</Text>
        </div>
      </Modal>
    </div>
  );
}

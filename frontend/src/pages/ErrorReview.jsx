import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Typography, Button, Modal, Input, Rate, Steps, Empty, Spin, message } from 'antd';
import { FileExclamationOutlined, CheckCircleOutlined, SyncOutlined, BulbOutlined } from '@ant-design/icons';
import { errorAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOutletContext } from 'react-router-dom';

const { Title, Text } = Typography;
const { TextArea } = Input;

const ERROR_TYPE_MAP = {
  KNOWLEDGE_GAP: { color: 'red', label: '知识型错误' },
  METHOD_ERROR: { color: 'orange', label: '方法型错误' },
  CARELESS: { color: 'blue', label: '粗心错误' },
  MISUNDERSTANDING: { color: 'purple', label: '理解偏差' },
  TIME_OUT: { color: 'gold', label: '时间不足' },
  OTHER: { color: 'gray', label: '其他' },
};

const EBBINGHAUS = ['1天后', '3天后', '7天后', '14天后', '30天后'];

export default function ErrorReview() {
  const [loading, setLoading] = useState(true);
  const [records, setRecords] = useState([]);
  const [reviewModal, setReviewModal] = useState(null);
  const [reflection, setReflection] = useState('');
  const [understanding, setUnderstanding] = useState(3);
  const { user } = useAuth();
  const { selectedCourseId } = useOutletContext();

  useEffect(() => {
    if (user?.id) loadRecords();
  }, [user?.id]);

  const loadRecords = async () => {
    setLoading(true);
    try {
      const res = await errorAPI.getRecords(user.id, { course_id: selectedCourseId });
      setRecords(res?.data || res || []);
    } catch (e) {
      setRecords([]);
    }
    setLoading(false);
  };

  const columns = [
    { title: '知识点', dataIndex: 'knowledgePointName', key: 'kpName' },
    {
      title: '错误类型', dataIndex: 'errorType', key: 'errorType',
      render: (t) => <Tag {...ERROR_TYPE_MAP[t] || {}}>{ERROR_TYPE_MAP[t]?.label || t || '未知'}</Tag>,
    },
    { title: '日期', dataIndex: 'createdAt', key: 'createdAt', render: (t) => t ? new Date(t).toLocaleDateString('zh-CN') : '-' },
    {
      title: '状态', dataIndex: 'isReviewed', key: 'isReviewed',
      render: (v) => v ? <Tag color="green" icon={<CheckCircleOutlined />}>已解决</Tag> : <Tag color="orange" icon={<SyncOutlined />}>待复盘</Tag>,
    },
    {
      title: '操作', key: 'action',
      render: (_, record) => !record.isReviewed && (
        <Button type="link" onClick={() => setReviewModal(record)}>开始复盘</Button>
      ),
    },
  ];

  const typeStats = {};
  records.forEach(r => {
    const t = r.errorType || 'OTHER';
    typeStats[t] = (typeStats[t] || 0) + 1;
  });

  const handleSubmitReview = async () => {
    if (!reflection.trim()) return;
    try {
      await errorAPI.submitReview({
        user_id: user?.id,
        record_id: reviewModal.id,
        reflection,
        understanding,
      });
      message.success('复盘提交成功');
      setRecords(prev => prev.map(r => r.id === reviewModal.id ? { ...r, isReviewed: true } : r));
    } catch (e) {
      message.error('提交失败');
    }
    setReviewModal(null);
    setReflection('');
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>📝 智能错题复盘与反思</Title>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card><Statistic title="总错题" value={records.length} prefix={<FileExclamationOutlined />} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="已解决" value={records.filter(r => r.isReviewed).length} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#52c41a' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="待复盘" value={records.filter(r => !r.isReviewed).length} prefix={<SyncOutlined spin />} valueStyle={{ color: '#faad14' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="待复习" value="-" prefix={<BulbOutlined />} valueStyle={{ color: '#1677ff' }} /></Card></Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={12}>
          <Card title="📊 错误类型分布" size="small">
            <Row gutter={8}>
              {Object.entries(typeStats).length === 0
                ? <Empty description="暂无错题数据" style={{ width: '100%' }} />
                : Object.entries(typeStats).map(([key, val]) => (
                    <Col span={6} key={key}>
                      <div style={{ textAlign: 'center', padding: 8 }}>
                        <Tag {...ERROR_TYPE_MAP[key]} style={{ fontSize: 14 }}>{val}题</Tag>
                        <div><Text type="secondary" style={{ fontSize: 12 }}>{ERROR_TYPE_MAP[key]?.label || key}</Text></div>
                      </div>
                    </Col>
                  ))
              }
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
        {records.length === 0
          ? <Empty description="暂无错题记录" />
          : <Table dataSource={records} columns={columns} rowKey="id" pagination={false} size="small" />
        }
      </Card>

      <Modal
        title={`🧠 错题复盘 - ${reviewModal?.knowledgePointName || ''}`}
        open={!!reviewModal}
        onCancel={() => setReviewModal(null)}
        onOk={handleSubmitReview}
        okText="提交复盘"
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>错误类型：</Text>
          <Tag {...ERROR_TYPE_MAP[reviewModal?.errorType] || {}}>{ERROR_TYPE_MAP[reviewModal?.errorType]?.label || reviewModal?.errorType}</Tag>
        </div>
        <div style={{ marginBottom: 12 }}>
          <Text strong>自我反思：</Text>
          <TextArea value={reflection} onChange={e => setReflection(e.target.value)}
            placeholder="这道题为什么错了？我的思路哪里出了问题？下次如何避免？" rows={4} />
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

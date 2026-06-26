import { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Typography, Button, Modal, Input, Rate, Tag as AntTag, Empty, Spin, message, Space } from 'antd';
import { FileExclamationOutlined, CheckCircleOutlined, SyncOutlined, BulbOutlined } from '@ant-design/icons';
import api, { errorAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOutletContext } from 'react-router-dom';

const { Title, Text, Paragraph } = Typography;
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
  const [viewRecord, setViewRecord] = useState(null);
  const [questionOptions, setQuestionOptions] = useState(null);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [reflection, setReflection] = useState('');
  const [understanding, setUnderstanding] = useState(3);
  const { user } = useAuth();
  const { selectedCourseId } = useOutletContext();

  useEffect(() => {
    if (user?.id) loadRecords();
  }, [user?.id, selectedCourseId]);

  // 页面可见性变化时自动刷新
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'visible' && user?.id) {
        loadRecords();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [user?.id, selectedCourseId]);

  const loadRecords = useCallback(async () => {
    setLoading(true);
    try {
      const res = await errorAPI.getRecords(user.id, { courseId: selectedCourseId });
      setRecords(res?.data || res || []);
    } catch (e) {
      setRecords([]);
    }
    setLoading(false);
  }, [user?.id, selectedCourseId]);

  const columns = [
    {
      title: '题目', dataIndex: 'questionContent', key: 'question', width: 260,
      render: (t) => <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: t }}>{t || '-'}</Text>,
    },
    { title: '知识点', dataIndex: 'knowledgePointName', key: 'kpName', width: 120 },
    {
      title: '我的答案', dataIndex: 'studentAnswer', key: 'myAnswer', width: 80,
      render: (v) => <Tag color="orange">{v || '-'}</Tag>,
    },
    {
      title: '正确答案', dataIndex: 'correctAnswer', key: 'correct', width: 80,
      render: (v) => <Tag color="green">{v || '-'}</Tag>,
    },
    {
      title: '错误类型', dataIndex: 'errorType', key: 'errorType', width: 110,
      render: (t) => <Tag {...ERROR_TYPE_MAP[t] || {}}>{ERROR_TYPE_MAP[t]?.label || t || '未知'}</Tag>,
    },
    { title: '日期', dataIndex: 'createdAt', key: 'date', width: 100, render: (t) => t ? new Date(t).toLocaleDateString('zh-CN') : '-' },
    {
      title: '状态', dataIndex: 'isReviewed', key: 'status', width: 90,
      render: (v) => v ? <Tag color="green" icon={<CheckCircleOutlined />}>已解决</Tag> : <Tag color="orange" icon={<SyncOutlined />}>待复盘</Tag>,
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, record) => (
        <Button type="link" size="small" onClick={async () => {
            if (record.isReviewed) {
              setViewRecord(record);
              setQuestionOptions(null);
              setOptionsLoading(true);
              try {
                const res = await api.get('/v1/questions/' + record.questionId);
                const q = res?.data || res;
                let opts = q.options || [];
                if (opts && typeof opts === 'object' && !Array.isArray(opts)) {
                  opts = Object.entries(opts).map(([k, v]) => ({ label: k, text: v }));
                } else if (Array.isArray(opts)) {
                  opts = opts.map(opt => {
                    if (typeof opt === 'object' && opt !== null && opt.label) return opt;
                    if (typeof opt === 'string') {
                      const m = opt.match(/^([A-Da-d])[)\.]\s*(.*)/);
                      return m ? { label: m[1], text: m[2] } : { label: String.fromCharCode(65 + opts.indexOf(opt)), text: opt };
                    }
                    return opt;
                  });
                }
                setQuestionOptions(opts);
              } catch (e) { /* ignore */ }
              setOptionsLoading(false);
            } else {
              setReviewModal(record);
              setQuestionOptions(null);
              setOptionsLoading(true);
              try {
                const res = await api.get('/v1/questions/' + record.questionId);
                const q = res?.data || res;
                let opts = q.options || [];
                if (opts && typeof opts === 'object' && !Array.isArray(opts)) {
                  opts = Object.entries(opts).map(([k, v]) => ({ label: k, text: v }));
                } else if (Array.isArray(opts)) {
                  opts = opts.map(opt => {
                    if (typeof opt === 'object' && opt !== null && opt.label) return opt;
                    if (typeof opt === 'string') {
                      const m = opt.match(/^([A-Da-d])[)\.]\s*(.*)/);
                      return m ? { label: m[1], text: m[2] } : { label: String.fromCharCode(65 + opts.indexOf(opt)), text: opt };
                    }
                    return opt;
                  });
                }
                setQuestionOptions(opts);
              } catch (e) { /* ignore */ }
              setOptionsLoading(false);
            }
          }}>{record.isReviewed ? '📖 查看' : '开始复盘'}</Button>
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
      const res = await errorAPI.submitReview(reviewModal.id, {
        errorId: reviewModal.id,
        notes: reflection,
        reviewAccuracy: understanding * 20,
      });
      message.success('复盘提交成功');
      const updated = res?.data || res;
      setRecords(prev => prev.map(r => r.id === reviewModal.id ? { ...r, ...(updated.id ? updated : { isReviewed: true }) } : r));
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
            <Space wrap>
              {EBBINGHAUS.map((d, i) => (
                <Tag key={i} color={i === 0 ? 'blue' : 'default'} style={{ padding: '4px 12px', fontSize: 13 }}>
                  {d}
                </Tag>
              ))}
            </Space>
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
        width={600}
      >
        <div style={{ marginBottom: 12, padding: 12, background: '#fffbe6', borderRadius: 6 }}>
          <Text strong>📌 题目：</Text>
          <Paragraph style={{ marginTop: 4, marginBottom: 0 }}>{reviewModal?.questionContent}</Paragraph>
          {questionOptions && questionOptions.length > 0 && (
            <div style={{ marginTop: 8 }}>
              {questionOptions.map((opt, i) => (
                <Tag key={i} style={{ marginBottom: 4, fontSize: 12, display: 'block', padding: '4px 8px' }} color={opt.label === reviewModal?.correctAnswer ? 'green' : opt.label === reviewModal?.studentAnswer ? 'red' : 'default'}>
                  <Text style={{ fontSize: 12 }}>{opt.label}. {opt.text}</Text>
                </Tag>
              ))}
            </div>
          )}
          {optionsLoading && <Spin size="small" style={{ marginTop: 8 }} />}
        </div>
        <Space style={{ marginBottom: 12 }}>
          <div style={{ padding: '4px 12px', background: '#fff2f0', borderRadius: 4 }}>
            <Text type="secondary">我的答案：</Text>
            <Text delete style={{ color: '#ff4d4f' }}>{reviewModal?.studentAnswer || '-'}</Text>
          </div>
          <div style={{ padding: '4px 12px', background: '#f6ffed', borderRadius: 4 }}>
            <Text type="secondary">正确答案：</Text>
            <Text strong style={{ color: '#52c41a' }}>{reviewModal?.correctAnswer || '-'}</Text>
          </div>
        </Space>
        <div style={{ marginBottom: 12 }}>
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

      {/* 已解决错题 — 只读查看弹窗 */}
      <Modal
        title={`📖 错题回顾 - ${viewRecord?.knowledgePointName || ''}`}
        open={!!viewRecord}
        onCancel={() => { setViewRecord(null); setQuestionOptions(null); }}
        footer={<Button type="primary" onClick={() => { setViewRecord(null); setQuestionOptions(null); }}>关闭</Button>}
        width={600}
      >
        <div style={{ marginBottom: 12, padding: 12, background: '#f6ffed', borderRadius: 6 }}>
          <Text strong>📌 题目：</Text>
          <Paragraph style={{ marginTop: 4, marginBottom: 0 }}>{viewRecord?.questionContent}</Paragraph>
          {questionOptions && questionOptions.length > 0 && (
            <div style={{ marginTop: 8 }}>
              {questionOptions.map((opt, i) => (
                <Tag key={i} style={{ marginBottom: 4, fontSize: 12, display: 'block', padding: '4px 8px' }}
                  color={opt.label === viewRecord?.correctAnswer ? 'green' : opt.label === viewRecord?.studentAnswer ? 'red' : 'default'}>
                  <Text style={{ fontSize: 12 }}>{opt.label}. {opt.text}</Text>
                </Tag>
              ))}
            </div>
          )}
          {optionsLoading && <Spin size="small" style={{ marginTop: 8 }} />}
        </div>
        <Space style={{ marginBottom: 12 }}>
          <div style={{ padding: '4px 12px', background: '#fff2f0', borderRadius: 4 }}>
            <Text type="secondary">我的答案：</Text>
            <Text delete style={{ color: '#ff4d4f' }}>{viewRecord?.studentAnswer || '-'}</Text>
          </div>
          <div style={{ padding: '4px 12px', background: '#f6ffed', borderRadius: 4 }}>
            <Text type="secondary">正确答案：</Text>
            <Text strong style={{ color: '#52c41a' }}>{viewRecord?.correctAnswer || '-'}</Text>
          </div>
        </Space>
        <div style={{ marginBottom: 12 }}>
          <Text strong>错误类型：</Text>
          <Tag {...ERROR_TYPE_MAP[viewRecord?.errorType] || {}}>{ERROR_TYPE_MAP[viewRecord?.errorType]?.label || viewRecord?.errorType}</Tag>
        </div>
        {viewRecord?.reviewSuggestion && (
          <div style={{ marginBottom: 12, padding: 12, background: '#f0f5ff', borderRadius: 6 }}>
            <Text strong>📝 复习笔记：</Text>
            <Paragraph style={{ marginTop: 4, marginBottom: 0, whiteSpace: 'pre-wrap' }}>{viewRecord.reviewSuggestion}</Paragraph>
          </div>
        )}
        {viewRecord?.reviewAccuracy != null && (
          <div style={{ marginBottom: 12 }}>
            <Text strong>🎯 复习正确率：</Text>
            <Tag color={viewRecord.reviewAccuracy >= 80 ? 'green' : viewRecord.reviewAccuracy >= 60 ? 'blue' : 'orange'}>
              {viewRecord.reviewAccuracy}%
            </Tag>
          </div>
        )}
        <div style={{ padding: 12, background: '#fff7e6', borderRadius: 6 }}>
          <Text type="secondary">💡 温故而知新，定期回顾错题有助于巩固知识点</Text>
        </div>
      </Modal>
    </div>
  );
}

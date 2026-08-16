import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Col, Row, Spin, Tag, Typography, message, Alert, Empty, Space } from 'antd';
import { ArrowLeftOutlined, FileTextOutlined, ReloadOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import { courtApi, MootCourtReportDto } from '../../api/courtApi';

const { Title, Text, Paragraph } = Typography;

const RESULT_TAG: Record<string, { label: string; color: string }> = {
  SUPPORT: { label: '支持原告诉请', color: 'red' },
  REJECT: { label: '驳回原告诉请', color: 'green' },
  PARTIAL: { label: '部分支持', color: 'orange' },
};

const PHASE_LABEL: Record<string, string> = {
  PRE: '课前判决',
  POST: '课后判决',
};

/**
 * 模拟法庭分析报告页
 * 对比课前/课后两份判决，展示 AI 生成的成长分析报告。
 */
const MootCourtReport: React.FC = () => {
  const { classroomId } = useParams<{ classroomId: string }>();
  const navigate = useNavigate();

  const [report, setReport] = useState<MootCourtReportDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);

  const loadReport = useCallback(async () => {
    setLoading(true);
    try {
      const data = await courtApi.getReport(classroomId!);
      setReport(data);
    } catch (err: any) {
      console.error('加载报告失败:', err);
      setReport(null);
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  useEffect(() => {
    if (classroomId) {
      loadReport();
    }
  }, [classroomId, loadReport]);

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const res = await courtApi.generateReport(classroomId!);
      message.success('报告生成成功');
      await loadReport();
    } catch (err: any) {
      console.error('生成报告失败:', err);
      message.error(err?.message || '生成报告失败，请确认已完成课前、课后两次判决');
    } finally {
      setGenerating(false);
    }
  };

  const renderJudgment = (data: any, phase: string) => {
    if (!data) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={`${PHASE_LABEL[phase]}尚未提交`}
        />
      );
    }
    const tag = RESULT_TAG[data.result] || { label: data.result, color: 'default' };
    return (
      <div>
        <Tag color={tag.color} style={{ fontSize: 13, marginBottom: 8 }}>
          {tag.label}
        </Tag>
        <Paragraph style={{ fontSize: 14, whiteSpace: 'pre-wrap', marginBottom: 0 }}>
          {data.reason || '（未填写判决理由）'}
        </Paragraph>
      </div>
    );
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" tip="加载报告..." />
      </div>
    );
  }

  return (
    <div style={{ padding: '0 4px' }}>
      {/* 顶部标题 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 8,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            📊 模拟法庭 · 分析报告
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            课前/课后两份判决对比，AI 生成学习成长分析
          </Text>
        </div>
        <Space>
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/student/classroom/${classroomId}/court`)}
            size="small"
          >
            返回法庭
          </Button>
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={loadReport}
            size="small"
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* 案件标题 */}
      {report?.case && (
        <Card size="small" style={{ marginBottom: 12 }}>
          <Text strong>案件：</Text>
          {report.case.caseTitle}
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
            {report.case.fact}
          </Text>
        </Card>
      )}

      {/* 两份判决对比 */}
      <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
        <Col xs={24} md={12}>
          <Card size="small" title="🔴 课前判决（PRE）" style={{ height: '100%' }}>
            {renderJudgment(report?.preJudgment, 'PRE')}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" title="🟢 课后判决（POST）" style={{ height: '100%' }}>
            {renderJudgment(report?.postJudgment, 'POST')}
          </Card>
        </Col>
      </Row>

      {/* 报告正文 */}
      <Card size="small">
        {report?.report ? (
          <div style={{ lineHeight: 1.9 }}>
            <ReactMarkdown>{report.report}</ReactMarkdown>
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16, textAlign: 'left' }}
              message="报告尚未生成"
              description="完成课前（PRE）和课后（POST）两次庭审判决后，点击下方按钮生成对比分析报告（约需 15-25 秒）。"
            />
            <Button
              type="primary"
              size="large"
              icon={<FileTextOutlined />}
              onClick={handleGenerate}
              loading={generating}
              disabled={!report?.preJudgment || !report?.postJudgment}
            >
              {!report?.preJudgment || !report?.postJudgment
                ? '需完成两次判决后方可生成'
                : '生成分析报告'}
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
};

export default MootCourtReport;

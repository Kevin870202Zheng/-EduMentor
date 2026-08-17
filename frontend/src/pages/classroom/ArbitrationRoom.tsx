import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Drawer,
  Input,
  Modal,
  Radio,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  message,
  Empty,
  Alert,
  Divider,
} from 'antd';
import {
  ArrowRightOutlined,
  FileTextOutlined,
  SendOutlined,
  AuditOutlined,
  BankOutlined,
} from '@ant-design/icons';
import {
  arbitrationApi,
  ArbitrationSessionDto,
  ArbitrationReportDto,
} from '../../api/arbitrationApi';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const STAGE_NAMES = ['陈述', '答辩', '举证质证', '辩论', '裁决'];

// ── 角色气泡样式（仲裁语境）──────────────────────────────────────
const ROLE_STYLE: Record<string, { bg: string; border: string; label: string; icon: string }> = {
  CLERK: { bg: '#fafafa', border: '#d9d9d9', label: '📜 记录员', icon: '📋' },
  PLAINTIFF_AI: { bg: '#fff1f0', border: '#ffa39e', label: '👨‍👦 申请人（原告）', icon: '🗣️' },
  DEFENDANT_AI: { bg: '#e6f7ff', border: '#91d5ff', label: '🏪 被申请人（被告）', icon: '🤷' },
  ARBITER_STUDENT: { bg: '#f6ffed', border: '#b7eb8f', label: '🧑‍⚖️ 仲裁员（你）', icon: '⚖️' },
};

const AWARD_RESULTS = [
  { value: 'SUPPORT', label: '支持申请人（原告）请求', color: 'red' },
  { value: 'REJECT', label: '驳回申请人（原告）请求', color: 'green' },
  { value: 'PARTIAL', label: '部分支持申请人（原告）请求', color: 'orange' },
];

const RESULT_LABEL: Record<string, string> = {
  SUPPORT: '支持申请人请求',
  REJECT: '驳回申请人请求',
  PARTIAL: '部分支持申请人请求',
};

/**
 * 仲裁人案例分析聊天室（全屏 Drawer 内嵌课堂学习页）。
 * 学生扮演仲裁人主动提问；AI 扮演无法律基础的普通老百姓原/被告（降智人设）。
 * 设计文档: .youcoder/plans/learning-directory-arbitration-design.html §4.9
 */
const ArbitrationRoom: React.FC<{
  open: boolean;
  onClose: () => void;
  kpId: string;
  kpName?: string;
  initialPhase?: 'PRE' | 'POST';
  onReportGenerated?: () => void;
}> = ({ open, onClose, kpId, kpName, initialPhase = 'PRE', onReportGenerated }) => {
  const [phase, setPhase] = useState<'PRE' | 'POST'>(initialPhase);
  const [session, setSession] = useState<ArbitrationSessionDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [input, setInput] = useState('');
  const [stageLoading, setStageLoading] = useState(false);

  // 裁决弹窗
  const [awardOpen, setAwardOpen] = useState(false);
  const [awardResult, setAwardResult] = useState('SUPPORT');
  const [awardReason, setAwardReason] = useState('');
  const [awardSubmitting, setAwardSubmitting] = useState(false);

  // 报告
  const [reportOpen, setReportOpen] = useState(false);
  const [reportData, setReportData] = useState<ArbitrationReportDto | null>(null);
  const [reportLoading, setReportLoading] = useState(false);

  const listRef = useRef<HTMLDivElement>(null);

  // ── 加载会话 ──────────────────────────────────────────────────
  const loadSession = useCallback(
    async (p: 'PRE' | 'POST') => {
      if (!kpId) return;
      setLoading(true);
      setSession(null);
      try {
        // 优先查询已有会话（不触发生成）；不存在则 start 创建
        try {
          const s = await arbitrationApi.getSession(kpId, p);
          setSession(s);
          setPhase(p);
        } catch {
          const s = await arbitrationApi.start(kpId, p);
          setSession(s);
          setPhase(p);
        }
      } catch (err: any) {
        console.error('启动仲裁会话失败:', err);
        message.error(err?.message || '启动仲裁失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    },
    [kpId],
  );

  useEffect(() => {
    if (open && kpId) {
      loadSession(initialPhase);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, kpId]);

  // 自动滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [session?.messages?.length, loading]);

  // ── 仲裁人发言 ────────────────────────────────────────────────
  const handleSend = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content || sending) return;
    setSending(true);
    try {
      const s = await arbitrationApi.sendMessage(kpId, phase, content);
      setSession(s);
      setInput('');
    } catch (err: any) {
      console.error('发送失败:', err);
      message.error(err?.message || '发送失败，请稍后重试');
    } finally {
      setSending(false);
    }
  };

  // ── 进入下一环节 ──────────────────────────────────────────────
  const handleNextStage = async () => {
    if (stageLoading) return;
    setStageLoading(true);
    try {
      const s = await arbitrationApi.nextStage(kpId, phase);
      setSession(s);
    } catch (err: any) {
      console.error('切换环节失败:', err);
      message.error(err?.message || '切换环节失败');
    } finally {
      setStageLoading(false);
    }
  };

  // ── 提交裁决书 ────────────────────────────────────────────────
  const handleSubmitAward = async () => {
    if (!awardReason.trim()) {
      message.warning('请填写裁决理由');
      return;
    }
    setAwardSubmitting(true);
    try {
      const s = await arbitrationApi.submitAward(kpId, {
        phase,
        result: awardResult,
        reason: awardReason.trim(),
      });
      setSession(s);
      setAwardOpen(false);
      setAwardReason('');
      message.success('裁决书已提交');
    } catch (err: any) {
      console.error('提交裁决失败:', err);
      message.error(err?.message || '提交裁决失败');
    } finally {
      setAwardSubmitting(false);
    }
  };

  // ── 查看/生成报告 ─────────────────────────────────────────────
  const openReport = async () => {
    setReportOpen(true);
    setReportLoading(true);
    try {
      let data = await arbitrationApi.getReport(kpId);
      if (!data.report) {
        await arbitrationApi.generateReport(kpId);
        data = await arbitrationApi.getReport(kpId);
        onReportGenerated?.();
      }
      setReportData(data);
    } catch (err: any) {
      console.error('获取报告失败:', err);
      message.error(err?.message || '获取报告失败（需先完成课前+课后两次裁决）');
      setReportOpen(false);
    } finally {
      setReportLoading(false);
    }
  };

  const caseData = session?.case;
  const status = session?.status;
  const stageIndex = session?.stageIndex ?? 0;
  const awarded = status === 'AWARDED' || status === 'REPORTED';
  const canAward = status === 'AWARD_READY' || status === 'HEARING' || status === 'OPENING';
  const phaseLabel = phase === 'PRE' ? '课前仲裁' : '课后仲裁';

  return (
    <Drawer
      title={null}
      width="100%"
      height="100%"
      open={open}
      onClose={onClose}
      closable={false}
      bodyStyle={{ padding: 16, background: '#f6f8fb' }}
    >
      {/* 顶部标题 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
          flexWrap: 'wrap',
          gap: 8,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            ⚖️ 案例分析·模拟仲裁{' '}
            <Tag color={phase === 'PRE' ? 'purple' : 'gold'}>{phaseLabel}</Tag>
            {kpName && (
              <Text type="secondary" style={{ fontSize: 13, marginLeft: 4 }}>
                {kpName}
              </Text>
            )}
          </Title>
          <Space size={8} style={{ marginTop: 4 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              环节进度：
            </Text>
            {STAGE_NAMES.map((name, i) => (
              <Tag
                key={name}
                color={i < stageIndex ? 'success' : i === stageIndex ? 'processing' : 'default'}
                style={{ fontSize: 11 }}
              >
                {i + 1}.{name}
              </Tag>
            ))}
            <Tag color={awarded ? 'success' : 'default'} style={{ fontSize: 11 }}>
              {awarded
                ? '✅ 已裁决'
                : status === 'CASE_GENERATING'
                  ? '⏳ 生成案件'
                  : status === 'AWARD_READY'
                    ? '⏳ 待裁决'
                    : '🟢 仲裁中'}
            </Tag>
          </Space>
        </div>
        <Space>
          {/* 阶段切换 */}
          <Button
            size="small"
            type={phase === 'PRE' ? 'primary' : 'default'}
            onClick={() => phase !== 'PRE' && loadSession('PRE')}
          >
            📋 课前仲裁
          </Button>
          <Button
            size="small"
            type={phase === 'POST' ? 'primary' : 'default'}
            onClick={() => phase !== 'POST' && loadSession('POST')}
          >
            📚 课后仲裁
          </Button>
          <Button size="small" icon={<FileTextOutlined />} onClick={openReport}>
            分析报告
          </Button>
          <Button size="small" icon={<BankOutlined />} onClick={onClose}>
            返回学习
          </Button>
        </Space>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 80 }}>
          <Spin size="large" tip="仲裁庭准备中..." />
        </div>
      ) : !session ? (
        <div style={{ padding: 24 }}>
          <Empty description="无法进入仲裁">
            <Button type="primary" onClick={() => loadSession(phase)}>
              重试
            </Button>
          </Empty>
        </div>
      ) : (
        <Row gutter={[12, 12]}>
          {/* 左侧：案件卡 + 快捷指令 */}
          <Col xs={24} md={8}>
            <Card size="small" style={{ marginBottom: 12 }}>
              <Text strong style={{ fontSize: 14 }}>
                📋 案件信息
              </Text>
              {caseData ? (
                <div style={{ marginTop: 8 }}>
                  <Paragraph style={{ fontSize: 13, marginBottom: 6 }}>
                    <Text strong>案件：</Text>
                    {caseData.caseTitle}
                  </Paragraph>
                  <Paragraph style={{ fontSize: 13, marginBottom: 6 }}>
                    <Text strong>案情：</Text>
                    {caseData.fact}
                  </Paragraph>
                  <Paragraph style={{ fontSize: 13, marginBottom: 6 }}>
                    <Text strong>争议焦点：</Text>
                  </Paragraph>
                  <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13 }}>
                    {caseData.disputes?.map((d: string, i: number) => (
                      <li key={i}>{d}</li>
                    ))}
                  </ul>
                  <Divider style={{ margin: '10px 0' }} />
                  <Paragraph style={{ fontSize: 13, marginBottom: 4 }}>
                    <Text strong>🗣️ 申请人（原告）：</Text>
                    {caseData.plaintiffName} — {caseData.plaintiffClaim}
                  </Paragraph>
                  <Paragraph style={{ fontSize: 13, marginBottom: 0 }}>
                    <Text strong>🤷 被申请人（被告）：</Text>
                    {caseData.defendantName} — {caseData.defendantDefense}
                  </Paragraph>
                </div>
              ) : (
                <Text type="secondary" style={{ fontSize: 13 }}>
                  案件生成中...
                </Text>
              )}
            </Card>

            {/* 快捷指令 */}
            <Card size="small">
              <Text strong style={{ fontSize: 14 }}>
                ⚡ 仲裁员快捷指令
              </Text>
              <Space direction="vertical" style={{ width: '100%', marginTop: 8 }} size={6}>
                <Button
                  block
                  size="small"
                  onClick={() => handleSend('请申请人（原告）把事情的经过和自己的要求再说清楚些。')}
                  disabled={sending || awarded}
                >
                  🗣️ 请原告陈述
                </Button>
                <Button
                  block
                  size="small"
                  onClick={() => handleSend('请被申请人（被告）回应一下，说说你的道理。')}
                  disabled={sending || awarded}
                >
                  🤷 请被告答辩
                </Button>
                <Button
                  block
                  size="small"
                  onClick={() => handleSend('请双方就争议焦点各自说说证据和看法。')}
                  disabled={sending || awarded}
                >
                  💬 双方举证辩论
                </Button>
                <Button
                  block
                  size="small"
                  type="dashed"
                  icon={<ArrowRightOutlined />}
                  onClick={handleNextStage}
                  loading={stageLoading}
                  disabled={awarded}
                >
                  进入下一环节
                </Button>
                <Button
                  block
                  size="small"
                  type="primary"
                  danger
                  icon={<AuditOutlined />}
                  onClick={() => setAwardOpen(true)}
                  disabled={awarded}
                >
                  ⚖️ 提交裁决书
                </Button>
              </Space>
            </Card>
          </Col>

          {/* 右侧：聊天室 */}
          <Col xs={24} md={16}>
            <Card
              size="small"
              style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
              bodyStyle={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 12 }}
            >
              <div
                ref={listRef}
                style={{
                  flex: 1,
                  overflowY: 'auto',
                  paddingRight: 4,
                  minHeight: 380,
                  maxHeight: 520,
                }}
              >
                {(session.messages || []).map((msg, idx) => {
                  const style = ROLE_STYLE[msg.role] || ROLE_STYLE.CLERK;
                  const isArbiter = msg.role === 'ARBITER_STUDENT';
                  return (
                    <div
                      key={idx}
                      style={{
                        marginBottom: 12,
                        display: 'flex',
                        justifyContent: isArbiter ? 'flex-end' : 'flex-start',
                      }}
                    >
                      <div
                        style={{
                          maxWidth: '85%',
                          padding: '10px 14px',
                          borderRadius: 12,
                          background: style.bg,
                          border: `1px solid ${style.border}`,
                        }}
                      >
                        <div style={{ marginBottom: 4, fontSize: 12, color: '#666' }}>
                          {style.label}
                        </div>
                        <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.7, fontSize: 14 }}>
                          {msg.content}
                        </div>
                      </div>
                    </div>
                  );
                })}
                {sending && (
                  <div style={{ textAlign: 'left', marginBottom: 8 }}>
                    <Spin size="small" />{' '}
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      对方正在发言...
                    </Text>
                  </div>
                )}
              </div>

              {/* 输入区 */}
              <div style={{ marginTop: 12 }}>
                {awarded ? (
                  <Alert
                    type="success"
                    showIcon
                    message={`本场仲裁已结束，裁决书已提交（${RESULT_LABEL[session?.awardData?.result || ''] || '已裁决'}）`}
                    description="完成课前+课后两次仲裁后，可前往「分析报告」查看 AI 生成的对比分析报告。"
                    action={
                      <Button size="small" type="primary" onClick={openReport}>
                        查看报告
                      </Button>
                    }
                  />
                ) : (
                  <>
                    <TextArea
                      rows={2}
                      placeholder="以仲裁人身份发言：向申请人/被申请人提问，或主持仲裁流程..."
                      value={input}
                      onChange={(e) => setInput(e.target.value)}
                      onPressEnter={(e) => {
                        if (!e.shiftKey) {
                          e.preventDefault();
                          handleSend();
                        }
                      }}
                      disabled={sending}
                    />
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        marginTop: 8,
                        gap: 8,
                      }}
                    >
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        💡 发言中提及「原告/被告」将指定对应方回应
                      </Text>
                      <Button
                        type="primary"
                        icon={<SendOutlined />}
                        loading={sending}
                        onClick={() => handleSend()}
                      >
                        发言
                      </Button>
                    </div>
                  </>
                )}
              </div>
            </Card>
          </Col>
        </Row>
      )}

      {/* 裁决书弹窗 */}
      <Modal
        title="⚖️ 提交裁决书"
        open={awardOpen}
        onOk={handleSubmitAward}
        onCancel={() => setAwardOpen(false)}
        okText="提交裁决"
        cancelText="再想想"
        confirmLoading={awardSubmitting}
        destroyOnClose
      >
        <Text strong>裁决结果：</Text>
        <Radio.Group
          value={awardResult}
          onChange={(e) => setAwardResult(e.target.value)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8, margin: '12px 0' }}
        >
          {AWARD_RESULTS.map((r) => (
            <Radio key={r.value} value={r.value}>
              <Tag color={r.color}>{r.label}</Tag>
            </Radio>
          ))}
        </Radio.Group>
        <Text strong>裁决理由：</Text>
        <TextArea
          rows={4}
          placeholder="请陈述你的裁决理由：依据本案事实，你认为谁更有道理、为什么..."
          value={awardReason}
          onChange={(e) => setAwardReason(e.target.value)}
          style={{ marginTop: 8 }}
        />
      </Modal>

      {/* 分析报告弹窗 */}
      <Modal
        title="📊 案例分析学习报告"
        open={reportOpen}
        onCancel={() => setReportOpen(false)}
        footer={null}
        width={860}
        destroyOnClose
      >
        {reportLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip="AI 正在对比两份裁决生成报告..." />
          </div>
        ) : reportData ? (
          <div>
            {reportData.case && (
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12 }}
                message={reportData.case.caseTitle}
                description={`争议焦点：${(reportData.case.disputes || []).join('；')}`}
              />
            )}
            <Row gutter={12} style={{ marginBottom: 12 }}>
              <Col span={12}>
                <Card size="small" title="📋 课前裁决">
                  <Tag color={reportData.preAward?.result === 'SUPPORT' ? 'red' : reportData.preAward?.result === 'REJECT' ? 'green' : 'orange'}>
                    {RESULT_LABEL[reportData.preAward?.result || ''] || '未裁决'}
                  </Tag>
                  <Paragraph style={{ marginTop: 8, fontSize: 13, whiteSpace: 'pre-wrap' }}>
                    {reportData.preAward?.reason || '（无理由）'}
                  </Paragraph>
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small" title="📚 课后裁决">
                  <Tag color={reportData.postAward?.result === 'SUPPORT' ? 'red' : reportData.postAward?.result === 'REJECT' ? 'green' : 'orange'}>
                    {RESULT_LABEL[reportData.postAward?.result || ''] || '未裁决'}
                  </Tag>
                  <Paragraph style={{ marginTop: 8, fontSize: 13, whiteSpace: 'pre-wrap' }}>
                    {reportData.postAward?.reason || '（无理由）'}
                  </Paragraph>
                </Card>
              </Col>
            </Row>
            {reportData.report ? (
              <div
                style={{
                  background: '#fafafa',
                  border: '1px solid #f0f0f0',
                  borderRadius: 8,
                  padding: '16px 20px',
                  fontSize: 14,
                  lineHeight: 1.9,
                  maxHeight: 420,
                  overflowY: 'auto',
                }}
                dangerouslySetInnerHTML={{ __html: renderMarkdown(reportData.report) }}
              />
            ) : (
              <Empty description="报告尚未生成，请先完成课前+课后两次仲裁" />
            )}
          </div>
        ) : null}
      </Modal>
    </Drawer>
  );
};

/** 简易 Markdown 渲染（标题/列表/粗体/换行），报告为 AI 生成的受限文本 */
function renderMarkdown(text: string): string {
  if (!text) return '';
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  html = html
    .replace(/^### (.*)$/gm, '<h4 style="margin:12px 0 6px;color:#1d39c4;">$1</h4>')
    .replace(/^## (.*)$/gm, '<h3 style="margin:14px 0 6px;color:#1d39c4;">$1</h3>')
    .replace(/^- (.*)$/gm, '<li style="margin:4px 0;">$1</li>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/\n/g, '<br/>');
  return `<p>${html}</p>`;
}

export default ArbitrationRoom;

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
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
import { courtApi, MootCourtSessionDto } from '../../api/courtApi';
import { classroomApi } from '../../api/classroomApi';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const STAGE_NAMES = ['陈述', '答辩', '举证质证', '法庭辩论', '判决'];

// ── 角色气泡样式 ────────────────────────────────────────────────
const ROLE_STYLE: Record<string, { bg: string; border: string; label: string; icon: string }> = {
  CLERK: { bg: '#fafafa', border: '#d9d9d9', label: '📜 书记员', icon: '⚖️' },
  PLAINTIFF_AI: { bg: '#fff1f0', border: '#ffa39e', label: '⚖️ 原告', icon: '🛡️' },
  DEFENDANT_AI: { bg: '#e6f7ff', border: '#91d5ff', label: '⚖️ 被告', icon: '🏛️' },
  JUDGE_STUDENT: { bg: '#f6ffed', border: '#b7eb8f', label: '🧑‍⚖️ 法官（你）', icon: '🔨' },
};

const JUDGMENT_RESULTS = [
  { value: 'SUPPORT', label: '支持原告诉讼请求', color: 'red' },
  { value: 'REJECT', label: '驳回原告诉讼请求', color: 'green' },
  { value: 'PARTIAL', label: '部分支持原告诉讼请求', color: 'orange' },
];

/**
 * 模拟法庭聊天室
 * 学生扮演法官，AI 扮演原告/被告进行庭审对抗；支持快捷指令、环节推进、判决提交。
 */
const MootCourtRoom: React.FC = () => {
  const { classroomId } = useParams<{ classroomId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [phase, setPhase] = useState<'PRE' | 'POST'>(
    searchParams.get('phase') === 'POST' ? 'POST' : 'PRE',
  );
  const [session, setSession] = useState<MootCourtSessionDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [input, setInput] = useState('');
  const [stageLoading, setStageLoading] = useState(false);

  // 判决弹窗
  const [judgmentOpen, setJudgmentOpen] = useState(false);
  const [judgmentResult, setJudgmentResult] = useState('SUPPORT');
  const [judgmentReason, setJudgmentReason] = useState('');
  const [judgmentSubmitting, setJudgmentSubmitting] = useState(false);

  const listRef = useRef<HTMLDivElement>(null);

  // ── 加载会话 ──────────────────────────────────────────────────
  const loadSession = useCallback(async (p: 'PRE' | 'POST') => {
    setLoading(true);
    try {
      // 优先查询已有会话（不触发生成）；不存在则 start 创建
      let s = await courtApi.getSession(classroomId!, p);
      setSession(s);
      setPhase(p);
    } catch {
      try {
        const s = await courtApi.start(classroomId!, p);
        setSession(s);
        setPhase(p);
      } catch (err: any) {
        console.error('启动模拟法庭失败:', err);
        message.error(err?.message || '启动模拟法庭失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  useEffect(() => {
    const init = async () => {
      if (!classroomId) return;
      // 优先使用 URL 显式指定阶段；否则按学习进度自动判定（学完→课后法庭，未学完→课前法庭）
      const explicitPhase = searchParams.get('phase');
      if (explicitPhase === 'PRE' || explicitPhase === 'POST') {
        loadSession(explicitPhase);
        return;
      }
      try {
        const progress = await classroomApi.getProgress(classroomId);
        loadSession(progress?.status === 'completed' ? 'POST' : 'PRE');
      } catch {
        loadSession('PRE');
      }
    };
    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classroomId]);

  // 自动滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [session?.messages?.length]);

  // ── 发送法官发言 ──────────────────────────────────────────────
  const handleSend = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content || sending) return;
    setSending(true);
    try {
      const s = await courtApi.sendMessage(classroomId!, phase, content);
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
      const s = await courtApi.nextStage(classroomId!, phase);
      setSession(s);
    } catch (err: any) {
      console.error('切换环节失败:', err);
      message.error(err?.message || '切换环节失败');
    } finally {
      setStageLoading(false);
    }
  };

  // ── 提交判决 ──────────────────────────────────────────────────
  const handleSubmitJudgment = async () => {
    if (!judgmentReason.trim()) {
      message.warning('请填写判决理由');
      return;
    }
    setJudgmentSubmitting(true);
    try {
      const s = await courtApi.submitJudgment(classroomId!, {
        phase,
        result: judgmentResult,
        reason: judgmentReason.trim(),
      });
      setSession(s);
      setJudgmentOpen(false);
      setJudgmentReason('');
      message.success('判决已提交');
    } catch (err: any) {
      console.error('提交判决失败:', err);
      message.error(err?.message || '提交判决失败');
    } finally {
      setJudgmentSubmitting(false);
    }
  };

  // ── 去报告页 ──────────────────────────────────────────────────
  const goReport = () => {
    navigate(`/student/classroom/${classroomId}/court/report`);
  };

  const caseData = session?.case;
  const status = session?.status;
  const stageIndex = session?.stageIndex ?? 0;
  const judged = status === 'JUDGED' || status === 'REPORTED';
  const canJudge = status === 'HEARING' || status === 'OPENING';
  const phaseLabel = phase === 'PRE' ? '课前法庭' : '课后法庭';

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" tip="法庭准备中..." />
      </div>
    );
  }

  if (!session) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="无法进入模拟法庭">
          <Button type="primary" onClick={() => loadSession(phase)}>
            重试
          </Button>
        </Empty>
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
            🎭 模拟法庭{' '}
            <Tag color={phase === 'PRE' ? 'purple' : 'gold'}>{phaseLabel}</Tag>
            {caseData?.caseTitle && (
              <Text type="secondary" style={{ fontSize: 13, marginLeft: 4 }}>
                {caseData.caseTitle}
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
            <Tag color={judged ? 'success' : 'default'} style={{ fontSize: 11 }}>
              {judged ? '✅ 已判决' : status === 'CASE_GENERATING' ? '⏳ 生成案例' : status === 'JUDGMENT_READY' ? '⏳ 待判决' : '🟢 庭审中'}
            </Tag>
          </Space>
        </div>
        <Space>
          <Button icon={<FileTextOutlined />} onClick={goReport} size="small">
            分析报告
          </Button>
          <Button
            type="primary"
            icon={<BankOutlined />}
            onClick={() => navigate('/student/classrooms')}
            size="small"
          >
            返回课堂列表
          </Button>
        </Space>
      </div>

      <Row gutter={[12, 12]}>
        {/* 左侧：案件卡 + 流程 */}
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
                <Paragraph style={{ fontSize: 13, marginTop: 8, marginBottom: 6 }}>
                  <Text strong>法律要点：</Text>
                </Paragraph>
                <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13 }}>
                  {caseData.legalPoints?.map((p: string, i: number) => (
                    <li key={i}>{p}</li>
                  ))}
                </ul>
                <Divider style={{ margin: '10px 0' }} />
                <Paragraph style={{ fontSize: 13, marginBottom: 4 }}>
                  <Text strong>🛡️ 原告：</Text>
                  {caseData.plaintiffName} — {caseData.plaintiffClaim}
                </Paragraph>
                <Paragraph style={{ fontSize: 13, marginBottom: 0 }}>
                  <Text strong>🏛️ 被告：</Text>
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
              ⚡ 法官快捷指令
            </Text>
            <Space direction="vertical" style={{ width: '100%', marginTop: 8 }} size={6}>
              <Button
                block
                size="small"
                onClick={() => handleSend('请原告进一步陈述你的主张和依据。')}
                disabled={sending || judged}
              >
                🛡️ 请原告陈述
              </Button>
              <Button
                block
                size="small"
                onClick={() => handleSend('请被告对原告的主张进行答辩。')}
                disabled={sending || judged}
              >
                🏛️ 请被告答辩
              </Button>
              <Button
                block
                size="small"
                onClick={() => handleSend('双方请围绕争议焦点发表意见。')}
                disabled={sending || judged}
              >
                💬 双方辩论
              </Button>
              <Button
                block
                size="small"
                type="dashed"
                icon={<ArrowRightOutlined />}
                onClick={handleNextStage}
                loading={stageLoading}
                disabled={judged}
              >
                进入下一环节
              </Button>
              <Button
                block
                size="small"
                type="primary"
                danger
                icon={<AuditOutlined />}
                onClick={() => setJudgmentOpen(true)}
                disabled={judged}
              >
                ⚖️ 提交判决
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
                minHeight: 360,
                maxHeight: 480,
              }}
            >
              {(session.messages || []).map((msg, idx) => {
                const style = ROLE_STYLE[msg.role] || ROLE_STYLE.CLERK;
                const isJudge = msg.role === 'JUDGE_STUDENT';
                return (
                  <div
                    key={idx}
                    style={{
                      marginBottom: 12,
                      display: 'flex',
                      justifyContent: isJudge ? 'flex-end' : 'flex-start',
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
                  <Spin size="small" /> <Text type="secondary" style={{ fontSize: 12 }}>对方正在发言...</Text>
                </div>
              )}
            </div>

            {/* 输入区 */}
            <div style={{ marginTop: 12 }}>
              {judged ? (
                <Alert
                  type="success"
                  showIcon
                  message="本场庭审已结束，判决已提交"
                  description="可前往「分析报告」查看两份判决的对比报告（需完成课前+课后两次庭审）。"
                  action={
                    <Button size="small" type="primary" onClick={goReport}>
                      查看报告
                    </Button>
                  }
                />
              ) : (
                <>
                  <TextArea
                    rows={2}
                    placeholder="以法官身份发言：向原告/被告提问，或主持庭审..."
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

      {/* 判决弹窗 */}
      <Modal
        title="⚖️ 提交判决书"
        open={judgmentOpen}
        onOk={handleSubmitJudgment}
        onCancel={() => setJudgmentOpen(false)}
        okText="提交判决"
        cancelText="再想想"
        confirmLoading={judgmentSubmitting}
        destroyOnClose
      >
        <Text strong>判决结果：</Text>
        <Radio.Group
          value={judgmentResult}
          onChange={(e) => setJudgmentResult(e.target.value)}
          style={{ display: 'flex', flexDirection: 'column', gap: 8, margin: '12px 0' }}
        >
          {JUDGMENT_RESULTS.map((r) => (
            <Radio key={r.value} value={r.value}>
              <Tag color={r.color}>{r.label}</Tag>
            </Radio>
          ))}
        </Radio.Group>
        <Text strong>判决理由：</Text>
        <TextArea
          rows={4}
          placeholder="请陈述你的判决理由，可引用本案事实、法律知识要点..."
          value={judgmentReason}
          onChange={(e) => setJudgmentReason(e.target.value)}
          style={{ marginTop: 8 }}
        />
      </Modal>
    </div>
  );
};

export default MootCourtRoom;

import { useState, useEffect, useCallback } from 'react';
import {
  Card, Typography, Tag, Button, Space, Spin, Empty, List,
  Modal, Input, Select, message, Row, Col, Alert,
} from 'antd';
import {
  ClockCircleOutlined, SendOutlined, PlusOutlined, FileSearchOutlined,
  SaveOutlined, RobotOutlined, CheckCircleOutlined, HistoryOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { useAuth } from '../context/AuthContext';
import { timeMachineApi } from '../api/timeMachineApi';
import MarkdownContent from './classroom/components/MarkdownContent';

echarts.use([LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer]);

const { Title, Text, Paragraph } = Typography;

const STAGE_NAMES = { PRIMARY: '小学', JUNIOR: '初中', SENIOR: '高中', UNIVERSITY: '大学' };
const STAGE_COLORS = { PRIMARY: 'blue', JUNIOR: 'orange', SENIOR: 'green', UNIVERSITY: 'purple' };

/** 解析快照 summary JSON */
function parseSummary(snapshot) {
  if (!snapshot?.summary) return null;
  if (typeof snapshot.summary === 'object') return snapshot.summary;
  try {
    return JSON.parse(snapshot.summary);
  } catch {
    return null;
  }
}

/**
 * ⏳ 成长时光机 — 跨学段自我对话与成长记录。
 * 认知深度跃迁曲线 / 成长档案 / 来自过去的信 / AI 学习报告。
 */
export default function TimeMachine() {
  const { user } = useAuth();
  const studentId = user?.id;

  const [loading, setLoading] = useState(true);
  const [archives, setArchives] = useState([]);
  const [letters, setLetters] = useState([]);
  const [currentStage, setCurrentStage] = useState(null);

  // 写信用 Modal
  const [writeOpen, setWriteOpen] = useState(false);
  const [writeStage, setWriteStage] = useState('PRIMARY');
  const [writeQuestion, setWriteQuestion] = useState('');
  const [aiQuestion, setAiQuestion] = useState('');
  const [creating, setCreating] = useState(false);

  // 回信 Modal
  const [answerLetter, setAnswerLetter] = useState(null);
  const [answerText, setAnswerText] = useState('');
  const [answering, setAnswering] = useState(false);

  // 学习报告
  const [reportOpen, setReportOpen] = useState(false);
  const [report, setReport] = useState('');
  const [reportLoading, setReportLoading] = useState(false);

  const load = useCallback(async () => {
    if (!studentId) return;
    setLoading(true);
    try {
      const res = await timeMachineApi.overview(studentId);
      const data = res?.data || res || {};
      setArchives(data.archives || []);
      setLetters(data.letters || []);
      setCurrentStage(data.currentStage || null);
    } catch (e) {
      message.error('加载时光机数据失败');
    }
    setLoading(false);
  }, [studentId]);

  useEffect(() => {
    load();
  }, [load]);

  // ─── 跃迁曲线数据 ───
  const curveOption = (() => {
    if (!archives.length) return null;
    const stageOrder = ['PRIMARY', 'JUNIOR', 'SENIOR', 'UNIVERSITY'];
    const points = archives
      .map(a => ({
        stage: a.stage,
        name: STAGE_NAMES[a.stage] || a.stage || '未知',
        summary: parseSummary(a),
        createdAt: a.createdAt,
      }))
      .sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || ''));

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['整体正确率'], top: 0 },
      grid: { left: 40, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: points.map(p => p.name) },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{
        name: '整体正确率',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: points.map(p => Math.round((p.summary?.accuracyRate || 0) * 100)),
        areaStyle: { color: 'rgba(22, 119, 255, 0.12)' },
        lineStyle: { color: '#1677ff', width: 3 },
      }],
    };
  })();

  // ─── 写一封信 ───
  const openWriteModal = () => {
    setWriteStage(currentStage || 'PRIMARY');
    setWriteQuestion('');
    setAiQuestion('');
    setWriteOpen(true);
  };

  const handleAiQuestion = async () => {
    setCreating(true);
    try {
      // 仅生成提问（不落库），供学生预览/修改后寄出
      const res = await timeMachineApi.createLetter({
        studentId, stage: writeStage, direction: 'PAST_TO_NOW', generateOnly: true,
      });
      const data = res?.data || res;
      setAiQuestion(data?.question || '');
      setWriteQuestion(data?.question || '');
      message.success('AI 已为你写好提问，可修改后寄出');
    } catch (e) {
      message.error('AI 生成提问失败，请手动输入');
    }
    setCreating(false);
  };

  const handleCreateLetter = async () => {
    if (!writeQuestion.trim()) { message.warning('请填写提问内容'); return; }
    setCreating(true);
    try {
      await timeMachineApi.createLetter({
        studentId, stage: writeStage, direction: 'PAST_TO_NOW', question: writeQuestion.trim(),
      });
      message.success('信件已寄出 ✉️');
      setWriteOpen(false);
      load();
    } catch (e) {
      message.error('寄信失败，请稍后重试');
    }
    setCreating(false);
  };

  // ─── 回信 ───
  const handleAnswer = async () => {
    if (!answerText.trim()) { message.warning('请写下你的回答'); return; }
    setAnswering(true);
    try {
      await timeMachineApi.answerLetter(answerLetter.id, answerText.trim());
      message.success('回信已保存 🌱');
      setAnswerLetter(null);
      setAnswerText('');
      load();
    } catch (e) {
      message.error('回信失败，请稍后重试');
    }
    setAnswering(false);
  };

  // ─── 归档快照 ───
  const handleArchive = async () => {
    try {
      await timeMachineApi.archive({ studentId });
      message.success('成长档案已归档 📂');
      load();
    } catch (e) {
      message.error('归档失败，请稍后重试');
    }
  };

  // ─── AI 学习报告 ───
  const handleReport = async () => {
    setReportOpen(true);
    setReportLoading(true);
    try {
      const res = await timeMachineApi.stageReport(studentId, currentStage || undefined);
      const data = res?.data || res;
      setReport(data?.report || '暂无报告');
    } catch (e) {
      setReport('报告生成失败，请稍后重试');
    }
    setReportLoading(false);
  };

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 120 }} />;
  }

  return (
    <div style={{ maxWidth: 1080, margin: '0 auto' }}>
      {/* 顶部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
        <Space>
          <ClockCircleOutlined style={{ fontSize: 22, color: '#1677ff' }} />
          <Title level={4} style={{ margin: 0 }}>⏳ 成长时光机</Title>
          {currentStage && (
            <Tag color={STAGE_COLORS[currentStage] || 'blue'}>
              {STAGE_NAMES[currentStage] || currentStage}学段
            </Tag>
          )}
        </Space>
        <Space wrap>
          <Button icon={<PlusOutlined />} onClick={openWriteModal}>写一封信</Button>
          <Button icon={<SaveOutlined />} onClick={handleArchive}>归档成长档案</Button>
          <Button type="primary" icon={<FileSearchOutlined />} onClick={handleReport}>生成学习报告</Button>
        </Space>
      </div>

      <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message="成长时光机"
        description="记录你跨学段的学习轨迹：从小学的启蒙认知到大学的深入应用。给过去的自己回一封信，看看这一路走了多远。" />

      {/* 跃迁曲线 */}
      <Card size="small" title="📈 认知深度跃迁曲线" style={{ marginBottom: 16 }}>
        {curveOption ? (
          <ReactECharts echarts={echarts} option={curveOption} style={{ height: 280 }} />
        ) : (
          <Empty description="暂无成长曲线数据，先「归档成长档案」吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      <Row gutter={16}>
        {/* 成长档案 */}
        <Col xs={24} lg={12}>
          <Card size="small" title={`📂 成长档案（${archives.length}）`} style={{ marginBottom: 16, height: '100%' }}>
            {archives.length === 0 ? (
              <Empty description="暂无归档记录">
                <Text type="secondary" style={{ fontSize: 12 }}>
                  学段晋升时自动归档，也可点击右上角「归档成长档案」手动归档。
                </Text>
              </Empty>
            ) : (
              <List
                size="small"
                dataSource={archives}
                renderItem={(a, idx) => {
                  const summary = parseSummary(a);
                  const stageName = STAGE_NAMES[a.stage] || a.stage || '未知学段';
                  const acc = summary?.accuracyRate != null ? Math.round(summary.accuracyRate * 100) : null;
                  return (
                    <List.Item style={{ padding: '8px 0' }}>
                      <Space direction="vertical" size={2} style={{ width: '100%' }}>
                        <Space>
                          <Tag color={STAGE_COLORS[a.stage] || 'default'}>{idx + 1}. {stageName}学段</Tag>
                          {acc != null && <Text strong>{acc}%</Text>}
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {summary?.totalQuestions || 0} 题
                          </Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {a.createdAt ? new Date(a.createdAt).toLocaleDateString('zh-CN') : ''}
                          </Text>
                        </Space>
                        {summary?.themeMastery?.length > 0 && (
                          <div style={{ marginTop: 4 }}>
                            {summary.themeMastery.slice(0, 4).map(tm => (
                              <Tag key={tm.themeId} style={{ marginBottom: 4 }}>
                                {tm.themeName} {Math.round((tm.mastery || 0) * 100)}%
                              </Tag>
                            ))}
                          </div>
                        )}
                      </Space>
                    </List.Item>
                  );
                }}
              />
            )}
          </Card>
        </Col>

        {/* 来自过去的信 */}
        <Col xs={24} lg={12}>
          <Card size="small" title={`✉️ 来自过去的信（${letters.length}）`} style={{ height: '100%' }}>
            {letters.length === 0 ? (
              <Empty description="还没有信件">
                <Text type="secondary" style={{ fontSize: 12 }}>
                  写一封信给未来的自己，或让 AI 模拟过去的你提出一个困惑。
                </Text>
              </Empty>
            ) : (
              <List
                size="small"
                dataSource={letters}
                renderItem={(letter) => (
                  <List.Item style={{ padding: '8px 0', alignItems: 'flex-start' }}>
                    <Space direction="vertical" size={2} style={{ width: '100%' }}>
                      <Space wrap>
                        <Tag color={STAGE_COLORS[letter.stage] || 'default'}>
                          {STAGE_NAMES[letter.stage] || letter.stage || '过去'}的我
                        </Tag>
                        {letter.aiGenerated && <Tag icon={<RobotOutlined />} color="cyan" style={{ fontSize: 10 }}>AI</Tag>}
                        {letter.answered ? <Tag color="success" style={{ fontSize: 10 }}>已回信</Tag> : <Tag color="warning" style={{ fontSize: 10 }}>待回信</Tag>}
                      </Space>
                      <Text>「{letter.question}」</Text>
                      {letter.answer ? (
                        <div style={{ background: '#f6ffed', padding: '6px 10px', borderRadius: 6, width: '100%' }}>
                          <Text type="secondary" style={{ fontSize: 12 }}>我的回信：</Text>
                          <Paragraph style={{ margin: 0, fontSize: 13 }}>{letter.answer}</Paragraph>
                        </div>
                      ) : (
                        <Button size="small" type="primary" ghost icon={<SendOutlined />}
                          onClick={() => { setAnswerLetter(letter); setAnswerText(''); }}>
                          现在回信
                        </Button>
                      )}
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* ─── 写一封信 Modal ─── */}
      <Modal title="✉️ 写一封信（来自过去的信）" open={writeOpen}
        onCancel={() => setWriteOpen(false)} footer={null} width={560}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong>写信人学段（过去的自己）：</Text>
            <div style={{ marginTop: 8 }}>
              <Select
                style={{ width: 200 }}
                value={writeStage}
                onChange={setWriteStage}
                options={Object.entries(STAGE_NAMES).map(([k, v]) => ({ value: k, label: `🏫 ${v}` }))}
              />
            </div>
          </div>
          <div>
            <Space style={{ marginBottom: 8 }}>
              <Text strong>提问内容：</Text>
              <Button size="small" icon={<RobotOutlined />} loading={creating} onClick={handleAiQuestion}>
                AI 帮我提问
              </Button>
            </Space>
            <Input.TextArea rows={3} value={writeQuestion}
              onChange={e => setWriteQuestion(e.target.value)}
              placeholder="写下这个学段的你感到困惑的问题，或让 AI 帮你生成" />
            {aiQuestion && (
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
                💡 AI 提议：{aiQuestion}
              </Text>
            )}
          </div>
          <div style={{ textAlign: 'right' }}>
            <Button style={{ marginRight: 8 }} onClick={() => setWriteOpen(false)}>取消</Button>
            <Button type="primary" icon={<SendOutlined />} loading={creating} onClick={handleCreateLetter}>
              寄出信件
            </Button>
          </div>
        </Space>
      </Modal>

      {/* ─── 回信 Modal ─── */}
      <Modal title={`✉️ 回信给${answerLetter ? (STAGE_NAMES[answerLetter.stage] || answerLetter.stage || '过去') : ''}的自己`}
        open={!!answerLetter} onCancel={() => setAnswerLetter(null)} footer={null} width={560}>
        {answerLetter && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div style={{ background: '#f0f5ff', padding: 12, borderRadius: 8 }}>
              <Text type="secondary">TA 问：</Text>
              <Paragraph style={{ margin: 0 }}>「{answerLetter.question}」</Paragraph>
            </div>
            <Input.TextArea rows={5} value={answerText} onChange={e => setAnswerText(e.target.value)}
              placeholder="以现在的视角，写下你的回答与感悟…" />
            <div style={{ textAlign: 'right' }}>
              <Button style={{ marginRight: 8 }} onClick={() => setAnswerLetter(null)}>取消</Button>
              <Button type="primary" icon={<SendOutlined />} loading={answering} onClick={handleAnswer}>
                寄出回信
              </Button>
            </div>
          </Space>
        )}
      </Modal>

      {/* ─── 学习报告 Modal ─── */}
      <Modal title="📋 跨学段学习报告" open={reportOpen}
        onCancel={() => setReportOpen(false)} footer={null} width={720}>
        {reportLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}><Spin tip="AI 生成中…" /></div>
        ) : (
          <div style={{ maxHeight: '60vh', overflow: 'auto' }}>
            <MarkdownContent content={report} />
          </div>
        )}
      </Modal>
    </div>
  );
}

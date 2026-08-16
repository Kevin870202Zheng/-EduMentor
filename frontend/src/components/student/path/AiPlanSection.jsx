import { useState } from 'react';
import { Card, Input, Button, Drawer, message, Typography, Space, Divider, Tag, List, Avatar } from 'antd';
import { RobotOutlined, SendOutlined, FileAddOutlined, UserOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { pathApi } from '../../../api/pathApi';

const { Text, Paragraph } = Typography;

/**
 * AI 共创区 — 学生描述学习目标，AI 多轮对话追问/澄清，最终生成结构化路径。
 */
export default function AiPlanSection({ studentId, courseId, onGenerated }) {
  const [open, setOpen] = useState(false);
  const [goal, setGoal] = useState('');
  const [messages, setMessages] = useState([]);
  const [sessionId, setSessionId] = useState(null);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [generatedPath, setGeneratedPath] = useState(null);

  const openPanel = async () => {
    if (!goal.trim()) {
      message.warning('请先描述你的学习目标，例如：我想重点学行政法，为考研做准备');
      return;
    }
    setOpen(true);
    setMessages([{ role: 'user', content: goal.trim() }]);
    setGeneratedPath(null);
    setBusy(true);
    try {
      const res = await pathApi.aiPlanStart({ studentId, courseId, goal: goal.trim() });
      setSessionId(res.sessionId);
      setMessages(prev => [...prev, { role: 'assistant', content: res.reply }]);
    } catch (err) {
      setMessages(prev => [...prev, { role: 'assistant', content: `⚠️ ${err.message || '启动 AI 规划失败，请稍后重试'}` }]);
    }
    setBusy(false);
  };

  const send = async (generatePath = false) => {
    const content = generatePath
      ? input.trim() || '请基于以上讨论，为我生成一份学习路径'
      : input.trim();
    if (!content || !sessionId || busy) return;
    if (!generatePath) setInput('');

    setMessages(prev => [...prev, { role: 'user', content }]);
    setBusy(true);
    try {
      const res = await pathApi.aiPlanChat({
        studentId,
        sessionId,
        message: content,
        courseId,
        generatePath,
      });
      if (generatePath && res.path) {
        setGeneratedPath(res.path);
        setMessages(prev => [...prev, { role: 'assistant', content: res.reply }]);
      } else {
        setMessages(prev => [...prev, { role: 'assistant', content: res.reply }]);
      }
    } catch (err) {
      setMessages(prev => [...prev, { role: 'assistant', content: `⚠️ ${err.message || '请求失败'}` }]);
    }
    setBusy(false);
  };

  const confirmPath = () => {
    if (!generatedPath) return;
    onGenerated?.(generatedPath);
    setOpen(false);
    message.success(`路径「${generatedPath.name}」已生成，可在「我的路径」中查看`);
  };

  return (
    <Card
      title={<span><RobotOutlined style={{ color: '#722ed1', marginRight: 8 }} />AI 共创</span>}
      size="small"
    >
      <Space.Compact style={{ width: '100%' }}>
        <Input
          placeholder="描述你的学习目标，AI 帮你规划专属路径，如：我想重点学行政法，为考研做准备"
          value={goal}
          onChange={e => setGoal(e.target.value)}
          onPressEnter={openPanel}
          allowClear
        />
        <Button type="primary" icon={<RobotOutlined />} onClick={openPanel}>
          开始对话
        </Button>
      </Space.Compact>

      <Drawer
        title={<span><RobotOutlined style={{ color: '#722ed1', marginRight: 8 }} />AI 路径规划助手</span>}
        open={open}
        onClose={() => setOpen(false)}
        width={520}
        extra={
          <Text type="secondary" style={{ fontSize: 12 }}>
            {sessionId ? `会话 ${sessionId.slice(0, 8)}` : '新会话'}
          </Text>
        }
      >
        <div
          style={{
            height: 'calc(100vh - 220px)',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {/* 消息区 */}
          <div style={{ flex: 1, overflow: 'auto', paddingBottom: 12 }}>
            {messages.length === 0 && (
              <div style={{ textAlign: 'center', marginTop: 60, color: '#999' }}>
                <RobotOutlined style={{ fontSize: 40, color: '#d3adf7' }} />
                <p style={{ marginTop: 12 }}>告诉我你的学习目标，我会通过提问逐步了解你的需求，<br />最后为你生成专属学习路径。</p>
              </div>
            )}
            {messages.map((m, i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
                  marginBottom: 10,
                }}
              >
                <div
                  style={{
                    maxWidth: '78%',
                    padding: '8px 12px',
                    borderRadius: 10,
                    background: m.role === 'user' ? '#1677ff' : '#f0f0f0',
                    color: m.role === 'user' ? '#fff' : 'inherit',
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {m.content}
                </div>
              </div>
            ))}
            {busy && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#999', fontSize: 13 }}>
                <RobotOutlined spin /> AI 思考中…
              </div>
            )}
          </div>

          {/* 生成结果预览 */}
          {generatedPath && (
            <div style={{ marginBottom: 12, padding: 12, border: '1px solid #d3adf7', borderRadius: 8, background: '#f9f0ff' }}>
              <Text strong>✅ 路径已生成：{generatedPath.name}</Text>
              <div style={{ margin: '6px 0' }}>
                <Tag color="purple">AI 定制</Tag>
                <Tag>共 {generatedPath.totalNodes || generatedPath.nodes?.length || 0} 个节点</Tag>
                <Tag color="blue">状态：草稿</Tag>
              </div>
              <List
                size="small"
                dataSource={generatedPath.nodes || []}
                renderItem={(n, idx) => (
                  <List.Item style={{ padding: '4px 0' }}>
                    <Text style={{ fontSize: 13 }}>
                      {idx + 1}. {n.knowledgePointName}
                      {n.aiReason && <Text type="secondary" style={{ fontSize: 12 }}> — {n.aiReason}</Text>}
                    </Text>
                  </List.Item>
                )}
              />
              <Button type="primary" size="small" block icon={<ThunderboltOutlined />} onClick={confirmPath} style={{ marginTop: 8 }}>
                去查看我的路径
              </Button>
            </div>
          )}

          {/* 输入区 */}
          <Divider style={{ margin: '8px 0' }} />
          <div>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder="继续对话调整，如：更侧重实务、去掉太难的点、每天 1 小时"
                value={input}
                onChange={e => setInput(e.target.value)}
                onPressEnter={() => send(false)}
                disabled={!sessionId || busy}
                allowClear
              />
              <Button type="primary" icon={<SendOutlined />} onClick={() => send(false)} disabled={!sessionId || busy}>
                发送
              </Button>
            </Space.Compact>
            <Button
              type="dashed"
              icon={<FileAddOutlined />}
              block
              style={{ marginTop: 8 }}
              onClick={() => send(true)}
              loading={busy && messages[messages.length - 1]?.role === 'user' && generatedPath === null}
              disabled={!sessionId}
            >
              生成路径
            </Button>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 6 }}>
              对话确认需求后点击「生成路径」，AI 将从课程知识库中挑选知识点生成专属路径。
            </Text>
          </div>
        </div>
      </Drawer>
    </Card>
  );
}

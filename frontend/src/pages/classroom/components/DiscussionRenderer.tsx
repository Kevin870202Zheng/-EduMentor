import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Card, Input, Space, Tag, Typography, Progress } from 'antd';
import { BulbOutlined, SendOutlined, StepForwardOutlined } from '@ant-design/icons';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../../api/types';
import { slideUp, fadeIn, springBouncy } from '../animations';
import { apiClient } from '../../../api/apiClient';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

/** 讨论默认时长（毫秒）：60s，倒计时结束自动继续学习 */
const DEFAULT_DURATION = 60000;
/** 观点输入上限 */
const MAX_VIEW_LENGTH = 200;

interface DiscussionRendererProps {
  action: ActionDTO;
  /** 讨论结束 → 播放链恢复 */
  onAdvance?: () => void;
}

/**
 * 讨论动作渲染器（discussion）— 交互面板 v2
 * 小E提问 → 观点快捷选择(chips) / 自由输入 → 提交 → LLM 真实点评 → 继续学习
 * 60s 倒计时兜底：无人值守也自动继续，播放链不卡死
 */
const DiscussionRenderer: React.FC<DiscussionRendererProps> = ({ action, onAdvance }) => {
  const topic = action.topic || action.text || action.content || '';
  const promptText = action.prompt || '';
  const presets = action.options || [];
  const totalMs = Math.max(30000, action.duration || DEFAULT_DURATION);

  const [view, setView] = useState('');
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [replies, setReplies] = useState<string[]>([]);
  const [secondsLeft, setSecondsLeft] = useState(Math.round(totalMs / 1000));
  const [advancing, setAdvancing] = useState(false);
  const submittedRef = useRef(false);

  // ── 倒计时：到 0 自动继续学习（防卡死双出口之一） ──
  useEffect(() => {
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((totalMs - (Date.now() - startedAt)) / 1000));
      setSecondsLeft(left);
      if (left <= 0) {
        window.clearInterval(timer);
        submittedRef.current = true;
        setAdvancing(true);
        onAdvance?.();
      }
    }, 1000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const progress = Math.min(100, ((totalMs / 1000 - secondsLeft) / (totalMs / 1000)) * 100);

  // 选择预设观点
  const pickPreset = (preset: string) => {
    setSelectedPreset(preset);
    setView(preset);
  };

  // 自由输入时取消预设选中态
  const handleInput = (v: string) => {
    setView(v);
    if (selectedPreset && v !== selectedPreset) setSelectedPreset(null);
  };

  // 提交观点 → 小E LLM 点评
  const submitView = useCallback(async () => {
    const content = view.trim();
    if (!content || submitting) return;
    setSubmitting(true);
    try {
      const res: any = await apiClient.post('/v2/classrooms/discussion/reply', {
        topic: topic || undefined,
        prompt: promptText || undefined,
        studentView: content,
        options: presets,
      });
      const reply = res?.reply || res?.data?.reply || '';
      if (reply) setReplies((prev) => [...prev, reply]);
      submittedRef.current = true;
    } catch {
      setReplies((prev) => [...prev, '（小E 暂时走神了，我们继续学习吧～）']);
      submittedRef.current = true;
    } finally {
      setSubmitting(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, submitting, topic, promptText, presets]);

  // 继续学习（手动出口）
  const continueLearning = useCallback(() => {
    if (advancing) return;
    setAdvancing(true);
    onAdvance?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [advancing, onAdvance]);

  const canContinue = submittedRef.current || replies.length > 0;

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.text?.slice(0, 20) || 'discussion'}
        variants={slideUp}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{
          padding: '20px 24px',
          maxWidth: 860,
          margin: '0 auto',
          width: '100%',
          boxSizing: 'border-box',
        }}
      >
        {/* 倒计时进度条 */}
        <div style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              讨论时间剩余 <Text strong style={{ color: secondsLeft <= 10 ? '#ff4d4f' : undefined }}>{secondsLeft}s</Text>
            </Text>
            {canContinue && !advancing && (
              <Button
                type="primary"
                size="small"
                icon={<StepForwardOutlined />}
                onClick={continueLearning}
              >
                继续学习
              </Button>
            )}
          </div>
          <Progress
            percent={progress}
            showInfo={false}
            strokeColor={secondsLeft <= 10 ? '#ff4d4f' : '#722ed1'}
            size="small"
          />
        </div>

        {/* 小E 提问卡 */}
        <Card
          style={{
            borderRadius: 12,
            border: '1px solid #d4adfc',
            background: '#f9f0ff',
            marginBottom: 16,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <motion.div
              initial={{ scale: 0, rotate: -30 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={springBouncy}
              style={{
                width: 40,
                height: 40,
                borderRadius: '50%',
                background: '#d4adfc',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 20,
                flexShrink: 0,
              }}
            >
              💬
            </motion.div>
            <motion.div variants={fadeIn} style={{ flex: 1 }}>
              <Text strong style={{ color: '#722ed1', fontSize: 15, marginBottom: 4, display: 'block' }}>
                AI同学 小E
              </Text>
              <Paragraph style={{ margin: 0, fontSize: 15, lineHeight: 1.7 }}>
                {topic}
              </Paragraph>
              {promptText && (
                <Paragraph type="secondary" style={{ margin: '8px 0 0', fontSize: 14, lineHeight: 1.6 }}>
                  💡 {promptText}
                </Paragraph>
              )}
            </motion.div>
          </div>
        </Card>

        {/* 观点表达区 */}
        <Card
          style={{
            borderRadius: 12,
            border: '1px solid #e8e8ef',
            background: '#fff',
            marginBottom: 16,
          }}
        >
          <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 10 }}>
            你的观点
          </Text>

          {/* 预设观点 chips */}
          {presets.length > 0 && (
            <Space size={[8, 8]} wrap style={{ marginBottom: 12 }}>
              {presets.map((preset) => (
                <Tag
                  key={preset}
                  onClick={() => pickPreset(preset)}
                  style={{
                    cursor: 'pointer',
                    fontSize: 13,
                    padding: '6px 14px',
                    borderRadius: 20,
                    border: selectedPreset === preset ? '1px solid #722ed1' : '1px solid #d9d9d9',
                    background: selectedPreset === preset ? '#f9f0ff' : '#fafafa',
                    color: selectedPreset === preset ? '#722ed1' : '#555',
                    whiteSpace: 'normal',
                    height: 'auto',
                  }}
                >
                  {selectedPreset === preset ? '✓ ' : ''}{preset}
                </Tag>
              ))}
            </Space>
          )}

          <TextArea
            value={view}
            onChange={(e) => handleInput(e.target.value)}
            placeholder={presets.length ? '也可以自己说点什么…' : '说说你的想法（40~200字）…'}
            autoSize={{ minRows: 2, maxRows: 5 }}
            maxLength={MAX_VIEW_LENGTH}
            showCount
            style={{ fontSize: 14, borderRadius: 8 }}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={submitting}
              disabled={!view.trim()}
              onClick={submitView}
              style={{ background: '#722ed1', borderColor: '#722ed1' }}
            >
              发表观点
            </Button>
          </div>
        </Card>

        {/* 小E 回应流 */}
        <AnimatePresence>
          {replies.length > 0 && (
            <motion.div
              key="replies"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
            >
              {replies.map((r, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 10,
                    background: '#f9f0ff',
                    border: '1px solid #d4adfc',
                    borderRadius: 12,
                    padding: '10px 14px',
                  }}
                >
                  <div
                    style={{
                      width: 30,
                      height: 30,
                      borderRadius: '50%',
                      background: '#d4adfc',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 15,
                      flexShrink: 0,
                    }}
                  >
                    💬
                  </div>
                  <div style={{ flex: 1 }}>
                    <Text strong style={{ color: '#722ed1', fontSize: 13, display: 'block', marginBottom: 2 }}>
                      小E 回应
                    </Text>
                    <Paragraph style={{ margin: 0, fontSize: 14, lineHeight: 1.7, color: '#333' }}>
                      {r}
                    </Paragraph>
                  </div>
                </motion.div>
              ))}
              <div style={{ textAlign: 'center', marginTop: 4 }}>
                <Button
                  type="primary"
                  size="large"
                  icon={<StepForwardOutlined />}
                  onClick={continueLearning}
                  disabled={advancing}
                  style={{ background: '#722ed1', borderColor: '#722ed1', borderRadius: 24, padding: '0 32px' }}
                >
                  {advancing ? '继续中…' : '继续学习'}
                </Button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* 未提交时的提示（倒计时兜底说明） */}
        {!canContinue && !advancing && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Text type="secondary" style={{ fontSize: 13 }}>
              <BulbOutlined /> 倒计时结束后将自动继续 · 也可以随时点底部「下一动作」
            </Text>
          </div>
        )}
      </motion.div>
    </AnimatePresence>
  );
};

export default DiscussionRenderer;

import React, { useState, useEffect, useRef } from 'react';
import { Card, Radio, Button, Typography, Space, Alert, message, Progress } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, StepForwardOutlined, ReloadOutlined } from '@ant-design/icons';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO, QuizSubmitResponse } from '../../api/types';
import {
  scaleIn,
  staggerContainer,
  staggerItem,
  slideUp,
  springBouncy,
} from '../animations';

const { Text, Title, Paragraph } = Typography;

interface QuizRendererProps {
  action: ActionDTO;
  onSubmit: (selectedIndex: number) => Promise<QuizSubmitResponse | null>;
  disabled?: boolean;
  /** 手动前进到下一个 Action */
  onAdvance?: () => void;
}

/**
 * Quiz 渲染器 v2.0
 * 
 * 交互流程：
 *   选择答案 → 提交 → 显示结果反馈 + 倒计时 → 自动前进
 *               ↓ 失败
 *           显示错误 + 重试/跳过按钮
 */
const QuizRenderer: React.FC<QuizRendererProps> = ({ action, onSubmit, disabled, onAdvance }) => {
  const [selected, setSelected] = useState<number | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [result, setResult] = useState<QuizSubmitResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(0);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 清理倒计时
  const clearCountdown = () => {
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
  };

  useEffect(() => {
    return () => clearCountdown();
  }, []);

  const handleSubmit = async () => {
    if (selected === null) {
      message.warning('请选择一个答案');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const res = await onSubmit(selected);
      if (res) {
        setResult(res);
        setSubmitted(true);
        // 启动 3 秒倒计时自动前进
        setCountdown(3);
        countdownRef.current = setInterval(() => {
          setCountdown(prev => {
            if (prev <= 1) {
              clearCountdown();
              onAdvance?.();
              return 0;
            }
            return prev - 1;
          });
        }, 1000);
      } else {
        setError('提交失败：服务器无响应，请重试或跳过此题');
      }
    } catch (err: any) {
      setError(err?.message || '提交异常，请重试或跳过此题');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRetry = () => {
    setError(null);
    handleSubmit();
  };

  const options = action.options || [];
  const isCorrect = result?.isCorrect;

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.question?.slice(0, 30) || 'quiz'}
        variants={scaleIn}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{ padding: '16px', maxWidth: 800, margin: '0 auto' }}
      >
        <Card
          title={
            <Space>
              <motion.span
                initial={{ rotate: -20, scale: 0 }}
                animate={{ rotate: 0, scale: 1 }}
                transition={springBouncy}
              >
                📝
              </motion.span>
              <span>随堂练习</span>
            </Space>
          }
          style={{ borderRadius: 12, border: '1px solid #e8e8e8' }}
        >
          <div style={{ marginBottom: 20 }}>
            <Title level={4} style={{ marginTop: 0 }}>
              {action.question || '请回答以下问题'}
            </Title>
          </div>

          <Radio.Group
            value={selected}
            onChange={e => setSelected(e.target.value)}
            disabled={submitted || disabled}
            style={{ width: '100%' }}
          >
            <motion.div
              variants={staggerContainer}
              initial="hidden"
              animate="visible"
              style={{ width: '100%' }}
            >
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                {options.map((option, index) => {
                  const optionLabels = ['A', 'B', 'C', 'D', 'E', 'F'];
                  let optionStyle: React.CSSProperties = {
                    display: 'block',
                    padding: '12px 16px',
                    borderRadius: 8,
                    border: '1px solid #d9d9d9',
                    width: '100%',
                    cursor: submitted ? 'default' : 'pointer',
                  };

                  const isSelected = index === selected;
                  const isCorrectOption = submitted && index === action.correctIndex;
                  const isWrongSelection = submitted && isSelected && !isCorrectOption;

                  if (isCorrectOption) {
                    optionStyle = {
                      ...optionStyle,
                      borderColor: '#52c41a',
                      background: '#f6ffed',
                    };
                  } else if (isWrongSelection) {
                    optionStyle = {
                      ...optionStyle,
                      borderColor: '#ff4d4f',
                      background: '#fff2f0',
                    };
                  } else if (!submitted && isSelected) {
                    optionStyle = {
                      ...optionStyle,
                      borderColor: '#1677ff',
                      background: '#e6f4ff',
                    };
                  }

                  return (
                    <motion.div key={index} variants={staggerItem}>
                      <motion.div
                        animate={
                          isCorrectOption
                            ? { scale: [1, 1.04, 1], transition: springBouncy }
                            : isWrongSelection
                              ? { x: [0, -6, 6, -4, 4, 0], transition: { duration: 0.4 } }
                              : undefined
                        }
                      >
                        <Radio value={index} style={optionStyle}>
                          <Text strong>{optionLabels[index]}.</Text> {option}
                        </Radio>
                      </motion.div>
                    </motion.div>
                  );
                })}
              </Space>
            </motion.div>
          </Radio.Group>

          {/* ── 提交按钮（提交前） ── */}
          {!submitted && !error && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.5 }}
              style={{ marginTop: 20, textAlign: 'center' }}
            >
              <Button
                type="primary"
                size="large"
                onClick={handleSubmit}
                loading={submitting}
                disabled={selected === null || disabled}
                style={{ minWidth: 160, borderRadius: 8 }}
              >
                {submitting ? '提交中...' : '提交答案'}
              </Button>
            </motion.div>
          )}

          {/* ── 提交错误（显示错误 + 重试/跳过） ── */}
          {error && !submitted && (
            <motion.div
              variants={slideUp}
              initial="hidden"
              animate="visible"
              style={{ marginTop: 20 }}
            >
              <Alert
                type="error"
                showIcon
                message="提交失败"
                description={
                  <Space direction="vertical" size={8}>
                    <Text>{error}</Text>
                    <Space>
                      <Button size="small" icon={<ReloadOutlined />} onClick={handleRetry} loading={submitting}>
                        重试
                      </Button>
                      <Button size="small" icon={<StepForwardOutlined />} onClick={() => onAdvance?.()}>
                        跳过此题
                      </Button>
                    </Space>
                  </Space>
                }
              />
            </motion.div>
          )}

          {/* ── 提交成功（显示结果 + 倒计时） ── */}
          {submitted && result && (
            <motion.div
              variants={slideUp}
              initial="hidden"
              animate="visible"
              style={{ marginTop: 20 }}
            >
              <Alert
                type={isCorrect ? 'success' : 'error'}
                showIcon
                icon={
                  isCorrect ? (
                    <motion.span
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{ type: 'spring', stiffness: 500, damping: 15 }}
                    >
                      <CheckCircleOutlined />
                    </motion.span>
                  ) : (
                    <CloseCircleOutlined />
                  )
                }
                message={
                  <Space>
                    <span>{isCorrect ? '✅ 回答正确！' : '❌ 回答有误'}</span>
                    {countdown > 0 && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {countdown} 秒后自动继续...
                      </Text>
                    )}
                  </Space>
                }
                description={
                  <div>
                    <Paragraph style={{ marginBottom: 4 }}>
                      {result.explanation || action.explanation || ''}
                    </Paragraph>
                    {result.knowledgePointName && (
                      <div style={{ marginTop: 4, marginBottom: 4 }}>
                        <Text type="secondary">
                          📌 这道题考察的是【{result.knowledgePointName}】
                        </Text>
                      </div>
                    )}
                    {result.aiFeedback && (
                      <Text type="secondary">{result.aiFeedback}</Text>
                    )}
                    {result.masteryDelta !== undefined && (
                      <div style={{ marginTop: 8, marginBottom: 8 }}>
                        <Text type={isCorrect ? 'success' : 'warning'}>
                          {isCorrect
                            ? `掌握度 +${result.masteryDelta}%`
                            : `掌握度 ${result.masteryDelta}%`}
                        </Text>
                      </div>
                    )}
                    {/* 倒计时进度条 */}
                    {countdown > 0 && (
                      <div style={{ marginTop: 8 }}>
                        <Progress
                          percent={(countdown / 3) * 100}
                          showInfo={false}
                          size="small"
                          strokeColor={isCorrect ? '#52c41a' : '#ff4d4f'}
                        />
                      </div>
                    )}
                    {/* 手动继续按钮 */}
                    <div style={{ marginTop: 8 }}>
                      <Button
                        size="small"
                        icon={<StepForwardOutlined />}
                        onClick={() => { clearCountdown(); onAdvance?.(); }}
                      >
                        继续
                      </Button>
                    </div>
                  </div>
                }
              />
            </motion.div>
          )}
        </Card>
      </motion.div>
    </AnimatePresence>
  );
};

export default QuizRenderer;

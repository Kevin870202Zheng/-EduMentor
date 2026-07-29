import React, { useMemo } from 'react';
import { Typography } from 'antd';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../api/types';
import { slideUp, staggerContainer, staggerItem, fadeIn } from '../animations';

const { Text, Title } = Typography;

/**
 * 讲解动作渲染器（speech / speech_with_highlight）
 * 显示AI教师的讲解文本，支持入场动画和重点高亮脉冲
 * 当 isSpeaking=true 时显示 TTS 波形动画
 */
interface SpeechRendererProps {
  action: ActionDTO;
  isSpeaking?: boolean;
}

const SpeechRenderer: React.FC<SpeechRendererProps> = ({
  action,
  isSpeaking = false,
}) => {
  const isHighlight = action.type === 'speech_with_highlight';

  // 按换行分割文本为段落，用于逐段动画
  const paragraphs = useMemo(
    () => (action.text || '').split('\n').filter(Boolean),
    [action.text],
  );

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.text?.slice(0, 20) || 'empty'}
        variants={slideUp}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{ padding: '24px', maxWidth: 800, margin: '0 auto', position: 'relative' }}
      >
        <motion.div
          variants={fadeIn}
          style={{
            background: isHighlight ? '#fff7e6' : '#f6f8fa',
            borderRadius: 12,
            padding: '24px 32px',
            border: isHighlight ? '2px solid #faad14' : '1px solid #e8e8e8',
            boxShadow: isHighlight
              ? '0 2px 8px rgba(250,173,20,0.15)'
              : 'none',
            position: 'relative',
            overflow: 'hidden',
          }}
          animate={
            isHighlight
              ? {
                  borderColor: [
                    'rgba(250,173,20,0.6)',
                    'rgba(250,173,20,1)',
                    'rgba(250,173,20,0.6)',
                  ],
                  boxShadow: [
                    '0 2px 8px rgba(250,173,20,0.1)',
                    '0 4px 16px rgba(250,173,20,0.25)',
                    '0 2px 8px rgba(250,173,20,0.1)',
                  ],
                }
              : isSpeaking
                ? {
                    borderColor: [
                      'rgba(22,119,255,0.3)',
                      'rgba(22,119,255,0.6)',
                      'rgba(22,119,255,0.3)',
                    ],
                    boxShadow: [
                      '0 0 0 0 rgba(22,119,255,0.05)',
                      '0 0 12px 2px rgba(22,119,255,0.12)',
                      '0 0 0 0 rgba(22,119,255,0.05)',
                    ],
                  }
                : undefined
          }
          transition={
            isHighlight || isSpeaking
              ? { duration: 2, repeat: Infinity, ease: 'easeInOut' }
              : undefined
          }
        >
          {isHighlight && (
            <motion.div
              initial={{ opacity: 0, y: -8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
            >
              <Text
                type="warning"
                style={{ fontWeight: 600, marginBottom: 8, display: 'block' }}
              >
                ⭐ 重点内容
              </Text>
            </motion.div>
          )}

          {/* 逐段淡入的文字内容 */}
          <motion.div
            variants={staggerContainer}
            initial="hidden"
            animate="visible"
          >
            {paragraphs.length > 1 ? (
              paragraphs.map((para, i) => (
                <motion.div key={i} variants={staggerItem}>
                  <Text
                    style={{
                      fontSize: 16,
                      lineHeight: 1.8,
                      whiteSpace: 'pre-wrap',
                      display: 'block',
                      marginBottom: i < paragraphs.length - 1 ? 12 : 0,
                    }}
                  >
                    {para}
                  </Text>
                </motion.div>
              ))
            ) : (
              <motion.div variants={staggerItem}>
                <Text
                  style={{
                    fontSize: 16,
                    lineHeight: 1.8,
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {action.text}
                </Text>
              </motion.div>
            )}
          </motion.div>

          {/* TTS 波形视觉反馈 — 仅在播放语音时显示 */}
          {isSpeaking && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              style={{
                marginTop: 16,
                display: 'flex',
                alignItems: 'center',
                gap: 3,
                height: 24,
              }}
            >
              {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
                <motion.div
                  key={i}
                  style={{
                    width: 3,
                    borderRadius: 2,
                    background: isHighlight
                      ? 'linear-gradient(180deg, #faad14, #ffd591)'
                      : 'linear-gradient(180deg, #1677ff, #69b1ff)',
                  }}
                  animate={{
                    height: [4, 12 + Math.sin(i * 1.2) * 8, 4],
                    opacity: [0.4, 1, 0.4],
                  }}
                  transition={{
                    duration: 0.6 + (i % 3) * 0.15,
                    repeat: Infinity,
                    ease: 'easeInOut',
                    delay: i * 0.08,
                  }}
                />
              ))}
              <Text
                type="secondary"
                style={{ fontSize: 12, marginLeft: 8, opacity: 0.6 }}
              >
                正在讲解...
              </Text>
            </motion.div>
          )}
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
};

export default SpeechRenderer;

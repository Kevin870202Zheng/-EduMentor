import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../../api/types';

interface SubtitleBarProps {
  /** 当前语音动作（speech / speech_with_highlight / code_demo / show_slide.speech） */
  action: ActionDTO | null;
  /** 是否正在 TTS 播放 */
  isSpeaking: boolean;
  /** TTS 播放进度 0-100（用于逐字卡拉OK高亮） */
  ttsProgress: number;
  /** 无语音时的轻提示（如 pause_for_thought 显示"思考中"） */
  hint?: string | null;
}

/** 按句拆分文本（中文/英文标点） */
function splitSentences(text: string): string[] {
  const parts = text.match(/[^。！？；……!?;]+[。！？；……!?]?/g) || [];
  const trimmed = parts.map((p) => p.trim()).filter(Boolean);
  return trimmed.length ? trimmed : [text.trim()];
}

/**
 * 字幕条 — 语音轨渲染（v4.0 双轨模型）
 *
 * 视频网站式底部字幕：
 *   - 长 speech 按句拆分，逐句呈现（上一句暗显、当前句高亮）
 *   - 当前句内逐字卡拉OK高亮（随 TTS 进度推进）
 *   - TTS 播放时显示波形动画
 */
const SubtitleBar: React.FC<SubtitleBarProps> = ({
  action,
  isSpeaking,
  ttsProgress,
  hint,
}) => {
  const text = action?.text?.trim() || '';
  const isHighlight = action?.type === 'speech_with_highlight';

  const sentences = useMemo(() => splitSentences(text), [text]);
  const totalChars = useMemo(() => sentences.join('').length, [sentences]);

  // 由 TTS 进度计算当前字符索引（卡拉OK游标）
  const { currentIdx, charInSentence, prevChars } = useMemo(() => {
    if (!totalChars) return { currentIdx: 0, charInSentence: 0, prevChars: 0 };
    const charIndex = Math.floor((Math.min(ttsProgress, 100) / 100) * totalChars);
    let acc = 0;
    for (let i = 0; i < sentences.length; i += 1) {
      acc += sentences[i].length;
      if (charIndex < acc || i === sentences.length - 1) {
        return {
          currentIdx: i,
          charInSentence: Math.max(0, charIndex - (acc - sentences[i].length)),
          prevChars: acc - sentences[i].length,
        };
      }
    }
    return { currentIdx: 0, charInSentence: 0, prevChars: 0 };
  }, [sentences, totalChars, ttsProgress]);

  // 无内容：显示 hint（如"思考中"）或隐藏
  if (!text && !hint) return null;

  const currentSentence = sentences[currentIdx] || '';
  const prevSentence = currentIdx > 0 ? sentences[currentIdx - 1] : null;

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={text.slice(0, 24) || hint || 'empty'}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 12 }}
        transition={{ duration: 0.25 }}
        style={{
          position: 'relative',
          background: 'rgba(17, 17, 27, 0.82)',
          borderRadius: 12,
          padding: '10px 20px',
          minHeight: 52,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 -2px 12px rgba(0,0,0,0.12)',
          backdropFilter: 'blur(6px)',
        }}
      >
        {text ? (
          <div style={{ textAlign: 'center', maxWidth: 860 }}>
            {/* 上一句（暗显） */}
            {prevSentence && (
              <div
                style={{
                  fontSize: 14,
                  color: 'rgba(255,255,255,0.42)',
                  lineHeight: 1.6,
                  marginBottom: 2,
                }}
              >
                {prevSentence}
              </div>
            )}
            {/* 当前句（逐字卡拉OK高亮） */}
            <div
              style={{
                fontSize: isHighlight ? 20 : 17,
                fontWeight: isHighlight ? 700 : 500,
                lineHeight: 1.6,
                color: 'rgba(255,255,255,0.88)',
              }}
            >
              {Array.from(currentSentence).map((ch, i) => {
                const read = isSpeaking && (prevChars + i) < (prevChars + charInSentence);
                const done = !isSpeaking;
                return (
                  <span
                    key={`${i}-${ch}`}
                    style={{
                      color: read || done ? '#ffffff' : 'rgba(255,255,255,0.38)',
                      textShadow: read || done ? '0 0 8px rgba(255,255,255,0.35)' : 'none',
                      transition: 'color 0.12s ease',
                    }}
                  >
                    {ch}
                  </span>
                );
              })}
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'rgba(255,255,255,0.75)' }}>
            <span>💭</span>
            <span style={{ fontSize: 15 }}>{hint || '…'}</span>
          </div>
        )}

        {/* TTS 波形（仅在播放语音时显示） */}
        {isSpeaking && text && (
          <div
            style={{
              position: 'absolute',
              right: 18,
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              height: 20,
            }}
          >
            {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
              <motion.span
                key={i}
                style={{
                  width: 3,
                  borderRadius: 2,
                  background: isHighlight ? '#faad14' : '#69b1ff',
                }}
                animate={{
                  height: [4, 10 + Math.sin(i * 1.3) * 7, 4],
                  opacity: [0.4, 1, 0.4],
                }}
                transition={{
                  duration: 0.55 + (i % 3) * 0.12,
                  repeat: Infinity,
                  ease: 'easeInOut',
                  delay: i * 0.07,
                }}
              />
            ))}
          </div>
        )}
      </motion.div>
    </AnimatePresence>
  );
};

export default SubtitleBar;


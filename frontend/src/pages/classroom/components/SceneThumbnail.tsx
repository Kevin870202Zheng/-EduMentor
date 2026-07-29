import React, { useMemo } from 'react';
import { motion } from 'motion/react';
import type { SceneDetailDto, ActionDTO } from '../../api/types';

/**
 * SceneThumbnail — 场景缩略图组件
 *
 * 根据场景中的 Action 类型，显示不同风格的缩略图预览
 * - speech / speech_with_highlight → 文本摘要（取前三段关键句）
 * - quiz → "📝 N 道练习题"
 * - wb_draw_text / wb_draw_diagram → "📐 含图示内容"
 * - discussion → "💬 讨论主题"
 * - scene_transition → "🔄 场景过渡"
 * - pause_for_thought → "🤔 思考提示"
 */

interface SceneThumbnailProps {
  /** 场景数据 */
  scene: SceneDetailDto;
}

const SceneThumbnail: React.FC<SceneThumbnailProps> = ({ scene }) => {
  // 检测场景主要类型和生成摘要
  const { summaryType, summaryText, actionCount } = useMemo(() => {
    const actions = scene.actions || [];
    const count = actions.length;

    // 提取第一个非过渡/思考动作的类型
    const mainAction = actions.find(
      (a: ActionDTO) =>
        !['scene_transition', 'pause_for_thought'].includes(a.type),
    );
    const type = mainAction?.type || 'speech';

    let text = '';
    switch (type) {
      case 'speech':
      case 'speech_with_highlight':
        text =
          mainAction?.text?.split('\n').slice(0, 3).join(' ').slice(0, 60) ||
          scene.description?.slice(0, 60) ||
          '讲解内容';
        break;
      case 'wb_draw_text':
      case 'wb_draw_diagram':
        text =
          mainAction?.wbContent?.slice(0, 40) ||
          mainAction?.text?.slice(0, 40) ||
          '白板内容';
        break;
      case 'quiz':
        text = `${(mainAction?.options?.length || 0)} 道选项`;
        break;
      case 'discussion':
        text =
          mainAction?.text?.slice(0, 40) ||
          mainAction?.content?.slice(0, 40) ||
          '讨论主题';
        break;
      default:
        text = scene.description?.slice(0, 40) || '教学场景';
    }

    return {
      summaryType: type,
      summaryText: text,
      actionCount: count,
    };
  }, [scene]);

  // 类型图标和颜色
  const typeMeta = useMemo(() => {
    const meta: Record<string, { icon: string; color: string; label: string }> = {
      speech: { icon: '💬', color: '#1677ff', label: '讲解' },
      speech_with_highlight: { icon: '⭐', color: '#faad14', label: '重点' },
      wb_draw_text: { icon: '📝', color: '#52c41a', label: '白板' },
      wb_draw_diagram: { icon: '📐', color: '#52c41a', label: '图示' },
      quiz: { icon: '✍️', color: '#722ed1', label: '练习' },
      discussion: { icon: '💭', color: '#eb2f96', label: '讨论' },
      scene_transition: { icon: '🔄', color: '#13c2c2', label: '过渡' },
      pause_for_thought: { icon: '🤔', color: '#fa8c16', label: '思考' },
      code_demo: { icon: '💻', color: '#2f54eb', label: '代码' },
    };
    return meta[summaryType] || meta.speech;
  }, [summaryType]);

  return (
    <motion.div
      style={{
        width: '100%',
        aspectRatio: '16 / 9',
        borderRadius: 8,
        background: `linear-gradient(135deg, ${typeMeta.color}08, ${typeMeta.color}04)`,
        border: `1px solid ${typeMeta.color}20`,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        position: 'relative',
      }}
      whileHover={{ scale: 1.02 }}
      transition={{ type: 'spring', stiffness: 400, damping: 25 }}
    >
      {/* 顶部类型标签 */}
      <div
        style={{
          padding: '4px 8px',
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          fontSize: 11,
        }}
      >
        <span>{typeMeta.icon}</span>
        <span style={{ color: typeMeta.color, fontWeight: 600, fontSize: 10 }}>
          {typeMeta.label}
        </span>
        <span style={{ marginLeft: 'auto', color: '#bbb', fontSize: 9 }}>
          {actionCount} 动作
        </span>
      </div>

      {/* 内容预览区 */}
      <div
        style={{
          flex: 1,
          padding: '4px 8px 8px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
        }}
      >
        {summaryType === 'quiz' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
            {[0, 1, 2, 3].map((i) => (
              <div
                key={i}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  opacity: 0.7,
                }}
              >
                <div
                  style={{
                    width: 4,
                    height: 4,
                    borderRadius: '50%',
                    background: typeMeta.color,
                    opacity: 0.5,
                  }}
                />
                <div
                  style={{
                    height: 4,
                    width: `${40 + Math.random() * 40}%`,
                    borderRadius: 2,
                    background: `${typeMeta.color}15`,
                  }}
                />
              </div>
            ))}
          </div>
        ) : summaryType === 'discussion' ? (
          <div
            style={{
              fontSize: 11,
              color: '#666',
              lineHeight: 1.4,
              display: '-webkit-box',
              WebkitLineClamp: 3,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            💭 {summaryText}
          </div>
        ) : (
          <div
            style={{
              fontSize: 11,
              color: '#555',
              lineHeight: 1.5,
              display: '-webkit-box',
              WebkitLineClamp: 3,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              fontWeight: 500,
            }}
          >
            {summaryText}
          </div>
        )}
      </div>

      {/* 底部扫描光效 */}
      <motion.div
        style={{
          position: 'absolute',
          inset: 0,
          background: `linear-gradient(90deg, transparent, ${typeMeta.color}08, transparent)`,
          pointerEvents: 'none',
        }}
        initial={{ left: '-100%' }}
        animate={{ left: '200%' }}
        transition={{
          duration: 2.5,
          repeat: Infinity,
          repeatDelay: 4,
          ease: 'linear',
        }}
      />
    </motion.div>
  );
};

export default SceneThumbnail;

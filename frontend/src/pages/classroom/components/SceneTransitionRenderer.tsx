import React, { useState, useEffect, useCallback } from 'react';
import { Typography, Button, Tag } from 'antd';
import { motion, AnimatePresence } from 'motion/react';
import { StepForwardOutlined } from '@ant-design/icons';
import type { ActionDTO } from '../../api/types';
import { springBouncy, sceneSlide } from '../animations';

const { Text } = Typography;

/**
 * 场景过渡渲染器（scene_transition）
 * 显示场景切换动画/标题
 * 支持方向性滑动 + 场景信息展示 + 自动过渡 + 跳过
 *
 * Props 扩展（通过 action.params 传递）:
 * - direction: 1 | -1  前进/后退
 * - sceneNumber?: string  如 "2/5"
 * - knowledgePoints?: string[]  知识点标签
 * - onSkip?: () => void  跳过回调
 */
interface SceneTransitionRendererProps {
  action: ActionDTO;
  /** 过渡方向：1=前进, -1=后退 */
  direction?: 1 | -1;
  /** 场景序号 */
  sceneNumber?: string;
  /** 知识点标签 */
  knowledgePoints?: string[];
  /** 跳过回调（跳过过渡直接进入内容） */
  onSkip?: () => void;
}

const SceneTransitionRenderer: React.FC<SceneTransitionRendererProps> = ({
  action,
  direction = 1,
  sceneNumber,
  knowledgePoints = [],
  onSkip,
}) => {
  const [showSkip, setShowSkip] = useState(false);

  // 1.5s 后显示跳过按钮（避免用户一进来就点跳过看不到内容）
  useEffect(() => {
    const timer = setTimeout(() => setShowSkip(true), 1500);
    return () => clearTimeout(timer);
  }, []);

  const isSummary = action.content === 'summary';
  const icon = isSummary ? '📋' : '🎯';
  const gradientFrom = isSummary ? '#52c41a' : '#1677ff';
  const gradientTo = isSummary ? '#95de64' : '#69b1ff';

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.text || 'transition'}
        variants={sceneSlide(direction)}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '40px 24px',
          textAlign: 'center',
          position: 'relative',
          overflow: 'hidden',
          minHeight: 300,
        }}
      >
        {/* 背景渐变 */}
        <motion.div
          style={{
            position: 'absolute',
            inset: 0,
            background: `radial-gradient(circle at 50% 50%, ${gradientFrom}08, transparent 70%)`,
          }}
          animate={{
            scale: [1, 1.2, 1],
            opacity: [0.5, 1, 0.5],
          }}
          transition={{
            duration: 3,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
        />

        {/* 场景序号 */}
        {sceneNumber && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1, duration: 0.3 }}
            style={{ marginBottom: 20 }}
          >
            <Tag
              color="blue"
              style={{
                fontSize: 13,
                padding: '2px 16px',
                borderRadius: 12,
                fontWeight: 600,
              }}
            >
              场景 {sceneNumber}
            </Tag>
          </motion.div>
        )}

        {/* 知识点标签 */}
        {knowledgePoints.length > 0 && (
          <motion.div
            style={{ marginBottom: 16, display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'center' }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.2 }}
          >
            {knowledgePoints.map((kp, i) => (
              <Tag
                key={i}
                style={{
                  fontSize: 11,
                  padding: '0 8px',
                  borderRadius: 8,
                  background: '#e6f7ff',
                  border: '1px solid #91d5ff',
                  color: '#096dd9',
                }}
              >
                {kp}
              </Tag>
            ))}
          </motion.div>
        )}

        {/* 图标：旋转 + 弹跳进入 */}
        <motion.div
          initial={{ scale: 0, rotate: -180 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={springBouncy}
          style={{
            width: 70,
            height: 70,
            borderRadius: '50%',
            background: `linear-gradient(135deg, ${gradientFrom}, ${gradientTo})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 20,
            color: '#fff',
            fontSize: 28,
            boxShadow: `0 4px 20px ${gradientFrom}40`,
          }}
        >
          <motion.span
            animate={{
              rotate: [0, 10, -10, 0],
              scale: [1, 1.15, 1],
            }}
            transition={{
              duration: 2,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          >
            {icon}
          </motion.span>
        </motion.div>

        {/* 场景标题 */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.35, duration: 0.5 }}
        >
          <Text
            style={{
              fontSize: 22,
              fontWeight: 700,
              color: '#1a1a2e',
              lineHeight: 1.5,
            }}
          >
            {action.text || action.content || ''}
          </Text>
        </motion.div>

        {/* 副文案 */}
        {action.params?.subtitle && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.5 }}
            style={{ marginTop: 8 }}
          >
            <Text type="secondary" style={{ fontSize: 14 }}>
              {action.params.subtitle}
            </Text>
          </motion.div>
        )}

        {/* 过渡指示器 + 跳过按钮 */}
        <motion.div
          style={{
            marginTop: 28,
            display: 'flex',
            alignItems: 'center',
            gap: 16,
          }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.6 }}
        >
          {/* 跳动进度点 */}
          <div style={{ display: 'flex', gap: 8 }}>
            {[0, 1, 2, 3].map((i) => (
              <motion.div
                key={i}
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: gradientFrom,
                }}
                animate={{
                  opacity: [0.3, 1, 0.3],
                  scale: [0.8, 1.3, 0.8],
                  y: [0, -4, 0],
                }}
                transition={{
                  duration: 1.4,
                  repeat: Infinity,
                  delay: i * 0.18,
                  ease: 'easeInOut',
                }}
              />
            ))}
          </div>

          {/* 跳过按钮（延迟出现） */}
          {showSkip && onSkip && (
            <motion.div
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.3 }}
            >
              <Button
                size="small"
                icon={<StepForwardOutlined />}
                onClick={onSkip}
                style={{
                  borderRadius: 12,
                  fontSize: 12,
                  color: '#999',
                  borderColor: '#d9d9d9',
                }}
              >
                跳过
              </Button>
            </motion.div>
          )}
        </motion.div>

        {/* 顶部光效扫描 */}
        <motion.div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            height: 2,
            background: `linear-gradient(90deg, transparent, ${gradientFrom}, transparent)`,
            pointerEvents: 'none',
          }}
          animate={{ left: ['-100%', '200%'] }}
          transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
        />
      </motion.div>
    </AnimatePresence>
  );
};

export default SceneTransitionRenderer;

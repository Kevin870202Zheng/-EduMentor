import React from 'react';
import { Typography } from 'antd';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../api/types';
import { fadeIn } from '../animations';

const { Text } = Typography;

/**
 * 暂停思考渲染器（pause_for_thought）
 * 显示短暂的思考提示
 * 三点跳动加载 + 淡入淡出循环
 */
const PauseForThoughtRenderer: React.FC<{ action: ActionDTO }> = ({ action }) => {
  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.text || 'pause'}
        variants={fadeIn}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '32px 24px',
          gap: 12,
        }}
      >
        {/* 三点跳动动画 */}
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          {[0, 1, 2].map((i) => (
            <motion.div
              key={i}
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: '#bbb',
              }}
              animate={{
                y: [0, -8, 0],
                opacity: [0.4, 1, 0.4],
              }}
              transition={{
                duration: 0.8,
                repeat: Infinity,
                delay: i * 0.15,
                ease: 'easeInOut',
              }}
            />
          ))}
        </div>

        <motion.div
          animate={{
            opacity: [0.5, 1, 0.5],
          }}
          transition={{
            duration: 1.6,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
        >
          <Text type="secondary" style={{ fontSize: 15 }}>
            {action.text || '思考一下...'}
          </Text>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
};

export default PauseForThoughtRenderer;

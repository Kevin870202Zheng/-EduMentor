import React from 'react';
import { Card, Typography } from 'antd';
import { BulbOutlined } from '@ant-design/icons';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../api/types';
import { slideUp, fadeIn, springBouncy } from '../animations';

const { Text, Paragraph } = Typography;

/**
 * 讨论动作渲染器（discussion）
 * AI同学提问或邀请学生思考
 * 头像弹跳进入 + 文字渐显 + 思考提示脉冲
 */
const DiscussionRenderer: React.FC<{ action: ActionDTO }> = ({ action }) => {
  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={action.text?.slice(0, 20) || 'discussion'}
        variants={slideUp}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{ padding: '16px', maxWidth: 800, margin: '0 auto' }}
      >
        <Card
          style={{
            borderRadius: 12,
            border: '1px solid #d4adfc',
            background: '#f9f0ff',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            {/* 头像：弹跳进入 */}
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

            {/* 文字内容：渐显 */}
            <motion.div
              variants={fadeIn}
              style={{ flex: 1 }}
            >
              <Text strong style={{ color: '#722ed1', fontSize: 15, marginBottom: 4, display: 'block' }}>
                AI同学 小E
              </Text>
              <Paragraph style={{ margin: 0, fontSize: 15, lineHeight: 1.7 }}>
                {action.text || action.content || ''}
              </Paragraph>
            </motion.div>
          </div>

          {/* 思考提示脉冲 */}
          <motion.div
            style={{
              marginTop: 12,
              padding: '8px 12px',
              background: '#fff',
              borderRadius: 8,
            }}
            animate={{
              opacity: [0.6, 1, 0.6],
              scale: [1, 1.01, 1],
            }}
            transition={{
              duration: 2,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          >
            <Text type="secondary" style={{ fontSize: 13 }}>
              <BulbOutlined /> 思考一下这个问题，稍后AI教师会继续讲解
            </Text>
          </motion.div>
        </Card>
      </motion.div>
    </AnimatePresence>
  );
};

export default DiscussionRenderer;

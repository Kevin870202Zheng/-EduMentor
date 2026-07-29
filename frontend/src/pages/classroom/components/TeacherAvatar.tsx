import React, { useState, useEffect } from 'react';
import { Typography } from 'antd';
import { motion, AnimatePresence } from 'motion/react';
import { springBouncy } from '../animations';

const { Text } = Typography;

/**
 * AI 讲师角色配置
 */
export interface TeacherRole {
  /** 头衔/角色名称 */
  role: 'teacher' | 'assistant' | 'tutor';
  /** 显示名称 */
  name: string;
  /** 头像图片 URL（使用 emoji 作为占位，正式环境替换为真实头像） */
  avatar: string;
  /** 角色描述 */
  description: string;
  /** 主题色 */
  color: string;
}

/** 预设讲师角色 */
const PRESET_TEACHERS: Record<string, TeacherRole> = {
  teacher: {
    role: 'teacher',
    name: '赵老师',
    avatar: '🧑‍🏫',
    description: '资深讲师，擅长深入浅出地讲解知识点',
    color: '#1677ff',
  },
  assistant: {
    role: 'assistant',
    name: '小E',
    avatar: '🤖',
    description: 'AI助教，随时为你解答疑问',
    color: '#722ed1',
  },
  tutor: {
    role: 'tutor',
    name: '辅导老师',
    avatar: '👩‍🏫',
    description: '个性化辅导，帮你巩固薄弱环节',
    color: '#52c41a',
  },
};

/**
 * TeacherAvatar — AI 讲师头像组件
 *
 * 固定在播放器右上角，显示讲师形象。
 * 讲话时头像外圈脉冲辉光 + 呼吸缩放动画。
 * 首次出现时弹出角色介绍卡片。
 */
interface TeacherAvatarProps {
  /** 讲师角色 */
  role?: TeacherRole;
  /** 是否正在讲话 */
  isSpeaking?: boolean;
  /** 当前场景标题（角色卡显示） */
  sceneTitle?: string;
}

const TeacherAvatar: React.FC<TeacherAvatarProps> = ({
  role = PRESET_TEACHERS.teacher,
  isSpeaking = false,
  sceneTitle,
}) => {
  const [showRoleCard, setShowRoleCard] = useState(true);

  // 首次出现 3s 后自动隐藏角色卡
  useEffect(() => {
    const timer = setTimeout(() => setShowRoleCard(false), 3000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div style={{ position: 'relative' }}>
      {/* 角色介绍卡片 */}
      <AnimatePresence>
        {showRoleCard && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8, y: 10, x: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0, x: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: -10 }}
            transition={{ type: 'spring', stiffness: 400, damping: 25 }}
            style={{
              position: 'absolute',
              top: -120,
              right: 0,
              width: 200,
              background: '#fff',
              borderRadius: 12,
              padding: '12px 16px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.12)',
              border: `1px solid ${role.color}30`,
              zIndex: 100,
            }}
          >
            {/* 箭头 */}
            <div
              style={{
                position: 'absolute',
                bottom: -6,
                right: 24,
                width: 12,
                height: 12,
                background: '#fff',
                borderRight: `1px solid ${role.color}30`,
                borderBottom: `1px solid ${role.color}30`,
                transform: 'rotate(45deg)',
              }}
            />
            <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 4 }}>
              {role.avatar} {role.name}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {role.description}
            </Text>
            {sceneTitle && (
              <div
                style={{
                  marginTop: 8,
                  padding: '4px 8px',
                  background: `${role.color}10`,
                  borderRadius: 6,
                  fontSize: 11,
                  color: role.color,
                }}
              >
                当前场景: {sceneTitle}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 头像主体 */}
      <motion.div
        style={{
          width: 56,
          height: 56,
          borderRadius: '50%',
          background: `linear-gradient(135deg, ${role.color}, ${role.color}88)`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 28,
          cursor: 'pointer',
          position: 'relative',
          boxShadow: isSpeaking
            ? `0 0 20px ${role.color}40`
            : '0 2px 8px rgba(0,0,0,0.1)',
        }}
        // 呼吸缩放动画（仅在讲话时）
        animate={
          isSpeaking
            ? {
                scale: [1, 1.05, 1],
                boxShadow: [
                  `0 0 12px ${role.color}20`,
                  `0 0 24px ${role.color}50`,
                  `0 0 12px ${role.color}20`,
                ],
              }
            : { scale: 1 }
        }
        transition={
          isSpeaking
            ? { duration: 2, repeat: Infinity, ease: 'easeInOut' }
            : { duration: 0.3 }
        }
        onClick={() => setShowRoleCard((prev) => !prev)}
      >
        {role.avatar}

        {/* 讲话时的外圈脉冲环 */}
        {isSpeaking && (
          <motion.div
            style={{
              position: 'absolute',
              inset: -4,
              borderRadius: '50%',
              border: `2px solid ${role.color}40`,
            }}
            animate={{
              scale: [1, 1.12, 1],
              opacity: [0.6, 0, 0.6],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          />
        )}
      </motion.div>
    </div>
  );
};

export { PRESET_TEACHERS };
export default TeacherAvatar;

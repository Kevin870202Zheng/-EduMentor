// ================================================================
// animations.ts — 统一动画变体配置
// 所有动画定义集中在此文件，避免散落在各组件中
// 使用 transform 和 opacity 属性以保证 60fps 性能
//
// 用法：
//   正常情况下：import { fadeIn, slideUp, ... } 即可使用动画。
//   无障碍模式：import { noAnim, getVariants } from './animations';
//     getVariants(useReducedMotion(), slideUp) 会根据用户偏好
//     返回动画或无动画变体。
// ================================================================
import { type Variants, type Transition } from 'motion/react';

// ── Spring 预设 ──

/** 温和弹簧：适用于大多数入场 */
export const springGently: Transition = {
  type: 'spring',
  stiffness: 300,
  damping: 28,
  mass: 0.8,
};

/** 弹性弹簧：适用于强调/庆祝类动画 */
export const springBouncy: Transition = {
  type: 'spring',
  stiffness: 500,
  damping: 20,
  mass: 1.2,
};

/** 干脆弹簧：适用于点击反馈 */
export const springSnap: Transition = {
  type: 'spring',
  stiffness: 600,
  damping: 35,
};

// ── 入场变体 ──

/** 淡入淡出 */
export const fadeIn: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: springGently },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

/** 从下方滑入 */
export const slideUp: Variants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: springGently },
  exit: { opacity: 0, y: -12, transition: { duration: 0.15 } },
};

/** 缩放进入 */
export const scaleIn: Variants = {
  hidden: { opacity: 0, scale: 0.92 },
  visible: { opacity: 1, scale: 1, transition: springGently },
  exit: { opacity: 0, scale: 0.95, transition: { duration: 0.12 } },
};

/** 从右侧滑入 */
export const slideInRight: Variants = {
  hidden: { opacity: 0, x: 40 },
  visible: { opacity: 1, x: 0, transition: springGently },
  exit: { opacity: -40, x: 0, transition: { duration: 0.15 } },
};

/** 从左侧滑入 */
export const slideInLeft: Variants = {
  hidden: { opacity: 0, x: -40 },
  visible: { opacity: 1, x: 0, transition: springGently },
  exit: { opacity: 0, x: 40, transition: { duration: 0.15 } },
};

/** 场景方向性滑动过渡 */
export const sceneSlide = (direction: 1 | -1): Variants => ({
  hidden: { opacity: 0, x: direction * 60 },
  visible: { opacity: 1, x: 0, transition: springGently },
  exit: { opacity: 0, x: direction * -60, transition: { duration: 0.2 } },
});

// ── 逐项弹出（列表/选项用）──

/** 列表容器：子项使用 staggerItem 将逐个弹出 */
export const staggerContainer: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.1 },
  },
};

/** 列表子项：与 staggerContainer 配合使用 */
export const staggerItem: Variants = {
  hidden: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: springGently },
};

// ── 特殊变体 ──

/** 拼图块弹入（用于白板书写效果） */
export const writeIn: Variants = {
  hidden: { opacity: 0, clipPath: 'inset(0 100% 0 0)' },
  visible: {
    opacity: 1,
    clipPath: 'inset(0 0% 0 0)',
    transition: { duration: 0.6, ease: 'easeOut' },
  },
};

/** 抖动（用于错误反馈） */
export const shakeError: Variants = {
  hidden: { x: 0 },
  visible: {
    x: [0, -6, 6, -4, 4, 0],
    transition: { duration: 0.4 },
  },
};

// ── 无障碍：无动画变体 ──

/**
 * 无动画变体：立即显示内容，无任何动画效果。
 * 用于 prefers-reduced-motion 或用户关闭动画时替代所有动画变体。
 */
export const noAnim: Variants = {
  hidden: { opacity: 1 },
  visible: { opacity: 1, transition: { duration: 0 } },
  exit: { opacity: 1, transition: { duration: 0 } },
};

/**
 * 根据用户偏好返回动画或无动画变体
 *
 * 用法：
 *   const variants = getVariants(reducedMotion, slideUp);
 *   // 如果 reducedMotion=true，返回 noAnim；否则返回 slideUp
 */
export function getVariants(
  prefersReducedMotion: boolean,
  normalVariants: Variants,
): Variants {
  return prefersReducedMotion ? noAnim : normalVariants;
}

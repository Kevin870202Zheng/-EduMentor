// ================================================================
// perf.ts — 性能优化工具函数
//
// 提供动画优化、渲染优化相关的辅助函数。
// ================================================================

/**
 * 生成 will-change 样式值
 * 在动画元素上使用 will-change 提示浏览器提前优化
 */
export function willChangeTransform(): React.CSSProperties {
  return { willChange: 'transform' };
}

export function willChangeOpacity(): React.CSSProperties {
  return { willChange: 'opacity' };
}

export function willChangeTransformOpacity(): React.CSSProperties {
  return { willChange: 'transform, opacity' };
}

/**
 * GPU 加速的 transform 样式
 * 所有动画都优先使用 transform + opacity（避免重排）
 */
export const gpuAccelerated: React.CSSProperties = {
  transform: 'translateZ(0)',
  willChange: 'transform',
};

/**
 * 禁用动画的过渡（用于 prefers-reduced-motion）
 */
export const noAnimation: React.CSSProperties = {
  transition: 'none',
  animation: 'none',
};

/**
 * 动画时长常量（集中管理，便于调整）
 */
export const DURATION = {
  /** 标准入场动画 */
  enter: 0.35,
  /** 标准出场动画 */
  exit: 0.15,
  /** 列表逐项延迟 */
  stagger: 0.08,
  /** 场景过渡显示时长 */
  sceneTransition: 2000,
  /** 思考提示停顿时长 */
  pauseForThought: 1500,
  /** Quiz 提交后停顿 */
  quizFeedback: 2000,
} as const;

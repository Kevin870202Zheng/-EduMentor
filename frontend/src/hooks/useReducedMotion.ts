// ================================================================
// useReducedMotion.ts — 无障碍动画控制 Hook
//
// 检测用户是否启用了「减少动画」偏好（系统级 prefers-reduced-motion）
// 和/或 EduMentor 设置中的「减少动画」开关。
//
// 用法：
//   const prefersReducedMotion = useReducedMotion();
//   if (prefersReducedMotion) {
//     // 跳过动画直接显示
//   }
// ================================================================
import { useState, useEffect } from 'react';

// localStorage key for user animation preference
const STORAGE_KEY = 'edumentor_reduced_motion';

/**
 * 读取用户的动画偏好（本地设置）
 */
function getUserReducedMotionSetting(): boolean {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored !== null) {
      return stored === 'true';
    }
  } catch {
    // localStorage 不可用时忽略
  }
  return false;
}

/**
 * useReducedMotion — 检测用户是否偏好减少动画
 *
 * 返回 true 时表示用户希望减少/关闭动画。
 * 符合以下任一条件即为 true：
 * 1. 系统设置了 prefers-reduced-motion: reduce
 * 2. 用户手动在 EduMentor 设置中关闭了动画
 */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () => getUserReducedMotionSetting(),
  );

  // 监听系统级 prefers-reduced-motion
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handler = (e: MediaQueryListEvent) => {
      // 仅当用户未手动设置偏好时跟随系统
      if (localStorage.getItem(STORAGE_KEY) === null) {
        setReduced(e.matches);
      }
    };
    mq.addEventListener('change', handler);

    // 初始状态：系统偏好优先
    if (localStorage.getItem(STORAGE_KEY) === null) {
      setReduced(mq.matches);
    }

    return () => mq.removeEventListener('change', handler);
  }, []);

  return reduced;
}

/**
 * 设置用户动画偏好
 */
export function setReducedMotion(enabled: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(enabled));
  } catch {
    // ignore
  }
  // 触发所有 useReducedMotion hook 更新
  window.dispatchEvent(new StorageEvent('storage', {
    key: STORAGE_KEY,
    newValue: String(enabled),
  }));
}

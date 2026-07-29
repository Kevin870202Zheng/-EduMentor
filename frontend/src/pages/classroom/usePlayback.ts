import { useState, useCallback, useRef, useEffect } from 'react';
import type { ClassroomDetailDto, SceneDetailDto, ActionDTO } from '../../api/types';
import { classroomApi } from '../../api/classroomApi';
import { useTTSPlayer } from '../../hooks/useTTSPlayer';
import type { TTSPlayerState } from '../../hooks/useTTSPlayer';

export type PlaybackState = 'idle' | 'playing' | 'paused' | 'live';

/**
 * 播放器状态机 hook v3.0 — 彻底重构版
 *
 * 核心设计：
 *   1. 播放索引（sceneIndex / actionIndex）用 ref 维护，
 *      保证 TTS 回调和 setTimeout 中始终读取最新值，彻底消除闭包陷阱
 *   2. advanceAction() 是唯一的前进入口，用 isAdvancingRef 防重入
 *   3. executeAction() 是唯一的执行入口，负责渲染后调度 TTS / 定时器
 *   4. React state 仅用于触发 UI 更新，播放逻辑不依赖 state
 *
 * 播放链：
 *   executeAction → TTS play / setTimeout
 *       ↓                         ↓
 *   TTS onComplete → advanceAction → 更新 ref 索引 → setState → executeAction
 *   setTimeout 到期 → advanceAction → 更新 ref 索引 → setState → executeAction
 */
export function usePlayback(classroomId: string) {
  // ── React state（仅用于 UI 渲染） ──
  const [classroom, setClassroom] = useState<ClassroomDetailDto | null>(null);
  const [state, setState] = useState<PlaybackState>('idle');
  const [currentSceneIndex, setCurrentSceneIndex] = useState(0);
  const [currentActionIndex, setCurrentActionIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // ── ref 持有播放索引（回调中始终获取最新值） ──
  const sceneIndexRef = useRef(0);
  const actionIndexRef = useRef(0);
  /** 防止 advanceAction 重入 */
  const isAdvancingRef = useRef(false);
  /** 定时器引用 */
  const actionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** 课堂数据引用（供回调访问最新数据） */
  const classroomRef = useRef<ClassroomDetailDto | null>(null);

  // 派生数据（从 React state 计算，供组件渲染）
  const currentScene: SceneDetailDto | null =
    classroom?.scenes?.[currentSceneIndex] ?? null;
  const currentAction: ActionDTO | null =
    currentScene?.actions?.[currentActionIndex] ?? null;
  const totalScenes = classroom?.scenes?.length ?? 0;
  const totalActions = currentScene?.actions?.length ?? 0;
  const isLastScene = currentSceneIndex >= totalScenes - 1;
  const isLastAction = currentActionIndex >= totalActions - 1;
  const scenesCompleted = currentSceneIndex + (isLastAction ? 1 : 0);

  // ── 获取当前 action（从 ref 或 classroom 数据实时计算） ──
  const getCurrentAction = useCallback((): ActionDTO | null => {
    const c = classroomRef.current;
    if (!c) return null;
    const scene = c.scenes?.[sceneIndexRef.current] ?? null;
    return scene?.actions?.[actionIndexRef.current] ?? null;
  }, []);

  // ── 判断是否为 speech 类型 ──
  const isSpeechType = useCallback((type: string): boolean => {
    return type === 'speech' || type === 'speech_with_highlight' || type === 'code_demo';
  }, []);

  // ── 获取指定 action 的展示时长（毫秒） ──
  const getActionDelay = useCallback((action: ActionDTO): number => {
    if (action.duration != null && action.duration > 0) return action.duration;
    switch (action.type) {
      case 'scene_transition': return 2000;
      case 'pause_for_thought': return 1500;
      case 'wb_draw_text':
      case 'wb_draw_diagram': return 3000;
      case 'code_demo': return 4000;
      default: return 3000;
    }
  }, []);

  // ── 清除定时器 ──
  const clearTimers = useCallback(() => {
    if (actionTimerRef.current) {
      clearTimeout(actionTimerRef.current);
      actionTimerRef.current = null;
    }
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
  }, []);

  // ── 暂停所有播放 ──
  const pauseAll = useCallback(() => {
    clearTimers();
    ttsPlayer.pause();
  }, [clearTimers]);

  // ================================================================
  //  核心：前进到下一个 Action（唯一的前进入口）
  // ================================================================
  const advanceAction = useCallback(() => {
    if (isAdvancingRef.current) return;
    isAdvancingRef.current = true;

    // 停止当前播放
    ttsPlayer.stop();
    clearTimers();

    const c = classroomRef.current;
    if (!c) { isAdvancingRef.current = false; return; }

    const scenes = c.scenes;
    const currentSceneActions = scenes?.[sceneIndexRef.current]?.actions ?? [];

    // 计算下一个索引
    if (actionIndexRef.current + 1 < currentSceneActions.length) {
      // 同场景下一个 action
      actionIndexRef.current += 1;
    } else if (sceneIndexRef.current + 1 < scenes.length) {
      // 下一个场景
      sceneIndexRef.current += 1;
      actionIndexRef.current = 0;
    } else {
      // 课堂结束
      setState('idle');
      isAdvancingRef.current = false;
      classroomApi.completeClassroom(classroomId).catch(console.warn);
      return;
    }

    // 同步到 React state（触发 UI 渲染）
    setCurrentSceneIndex(sceneIndexRef.current);
    setCurrentActionIndex(actionIndexRef.current);
    isAdvancingRef.current = false;

    // 执行新 action（延迟一帧确保 React state 已更新）
    requestAnimationFrame(() => {
      executeAction();
      isAdvancingRef.current = false;
    });
  }, [classroomId, clearTimers]);

  // ================================================================
  //  核心：执行当前 Action（唯一的执行入口）
  // ================================================================
  const executeAction = useCallback(() => {
    if (state !== 'playing') return;

    const action = getCurrentAction();
    if (!action) return;

    const type = action.type;

    // Quiz / Discussion — 等待用户交互
    if (type === 'quiz' || type === 'discussion') return;

    // Speech 类型 — TTS 驱动
    if (isSpeechType(type)) {
      const text = action.text || '';
      if (text.trim()) {
        ttsPlayer.play(text, action.id || `${type}_${actionIndexRef.current}`);
        return;
      }
    }

    // 非 Speech、非交互类型 — 定时器驱动
    const delay = getActionDelay(action);
    actionTimerRef.current = setTimeout(() => {
      advanceAction();
    }, delay);
  }, [state, getCurrentAction, isSpeechType, getActionDelay]);

  // ================================================================
  //  TTS 播放器 — onComplete / onError 直接调用 advanceAction
  // ================================================================
  const ttsPlayer = useTTSPlayer({
    onComplete: () => {
      advanceAction();
    },
    onError: () => {
      advanceAction();
    },
  });

  // ── 暂停 ──
  const pause = useCallback(async () => {
    pauseAll();
    setState('paused');
    try { await classroomApi.pauseClassroom(classroomId); }
    catch { /* ignore */ }
  }, [classroomId, pauseAll]);

  // ── 恢复 ──
  const resume = useCallback(async () => {
    setState('playing');
  }, []);

  // ── 开始播放 ──
  const play = useCallback(async () => {
    if (state === 'playing') return;
    setState('playing');
    try { await classroomApi.startClassroom(classroomId); }
    catch { /* ignore */ }
  }, [classroomId, state]);

  // ── 后退到上一个 Action ──
  const prevAction = useCallback(() => {
    ttsPlayer.stop();
    clearTimers();
    const c = classroomRef.current;
    if (!c) return;

    if (actionIndexRef.current > 0) {
      actionIndexRef.current -= 1;
    } else if (sceneIndexRef.current > 0) {
      sceneIndexRef.current -= 1;
      const prevScene = c.scenes?.[sceneIndexRef.current];
      actionIndexRef.current = (prevScene?.actions?.length ?? 1) - 1;
    } else {
      return;
    }
    setCurrentSceneIndex(sceneIndexRef.current);
    setCurrentActionIndex(actionIndexRef.current);
    setState('paused');
  }, [clearTimers]);

  // ── 跳转到指定场景 ──
  const goToScene = useCallback((index: number) => {
    const c = classroomRef.current;
    if (!c || index < 0 || index >= (c.scenes?.length ?? 0)) return;
    ttsPlayer.stop();
    clearTimers();
    sceneIndexRef.current = index;
    actionIndexRef.current = 0;
    setCurrentSceneIndex(index);
    setCurrentActionIndex(0);
    setState('paused');
  }, [clearTimers]);

  // ── Quiz 提交 ──
  const submitQuiz = useCallback(async (selectedIndex: number) => {
    const c = classroomRef.current;
    const scene = c?.scenes?.[sceneIndexRef.current];
    if (!scene) return null;
    try {
      const result = await classroomApi.submitQuiz(scene.id, { sceneId: scene.id, selectedIndex });
      // 2s 后自动前进
      actionTimerRef.current = setTimeout(() => { advanceAction(); }, 2000);
      return result;
    } catch (err) {
      console.error('Failed to submit quiz:', err);
      return null;
    }
  }, []);

  // ── 进入/退出 live 模式 ──
  const enterLiveMode = useCallback(() => { pauseAll(); setState('live'); }, [pauseAll]);
  const exitLiveMode = useCallback(() => { setState('playing'); }, []);

  // ── 预缓存当前场景音频 ──
  const prefetchCurrentSceneAudio = useCallback(() => {
    const c = classroomRef.current;
    const scene = c?.scenes?.[sceneIndexRef.current];
    if (!scene?.actions) return;
    const texts = scene.actions
      .filter(a => isSpeechType(a.type) && a.text?.trim())
      .map(a => a.text!.trim());
    if (texts.length > 0) ttsPlayer.prefetch(texts);
  }, [isSpeechType]);

  // ── 加载课堂数据 ──
  const loadClassroom = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await classroomApi.getClassroom(classroomId);
      classroomRef.current = data;
      setClassroom(data);
    } catch (err: any) {
      setError(err?.message || '加载课堂失败');
    } finally {
      setLoading(false);
    }
  }, [classroomId]);

  // ═══════════════════════════════════════════════════════════════
  //  Effects
  // ═══════════════════════════════════════════════════════════════

  // 索引变化 → 执行新 action
  useEffect(() => {
    if (state === 'playing') {
      executeAction();
    }
    return () => { if (actionTimerRef.current) clearTimeout(actionTimerRef.current); };
  }, [currentSceneIndex, currentActionIndex, state, executeAction]);

  // 场景切换 → 预缓存音频
  useEffect(() => {
    if (state === 'playing') prefetchCurrentSceneAudio();
  }, [currentSceneIndex, state, prefetchCurrentSceneAudio]);

  // 进度定时上报
  useEffect(() => {
    if (state === 'playing') {
      progressTimerRef.current = setInterval(() => {
        const c = classroomRef.current;
        const scene = c?.scenes?.[sceneIndexRef.current];
        if (scene) {
          classroomApi.updateProgress(classroomId, scene.id, actionIndexRef.current).catch(() => {});
        }
      }, 5000);
    }
    return () => { if (progressTimerRef.current) clearInterval(progressTimerRef.current); };
  }, [state, classroomId]);

  // 初始加载
  useEffect(() => { loadClassroom(); }, [loadClassroom]);

  // 清理
  useEffect(() => {
    return () => { clearTimers(); ttsPlayer.stop(); };
  }, [clearTimers]);

  return {
    classroom, state, loading, error,
    currentScene, currentSceneIndex, currentAction, currentActionIndex,
    totalScenes, totalActions, scenesCompleted, isLastScene, isLastAction,
    ttsState: ttsPlayer.state,
    ttsProgress: ttsPlayer.progress,
    ttsCurrentActionId: ttsPlayer.currentActionId,
    play, pause, resume, enterLiveMode, exitLiveMode,
    nextAction: advanceAction,
    prevAction, goToScene, submitQuiz, loadClassroom,
  };
}

// ================================================================
// useTTSPlayer.ts — TTS 播放控制 React Hook
//
// 职责：
//   1. 音频状态管理（idle/playing/paused/error）
//   2. 播放控制 play/pause/stop/resume
//   3. 音频元素生命周期管理
//   4. 播放完成回调（供 usePlayback 接续自动播放链）
// ================================================================
import { useState, useCallback, useRef, useEffect } from 'react';
import { synthesize, prefetchSceneAudios, stopBrowserTTS } from '../services/ttsService';

// ─── 类型定义 ───

export type TTSPlayerState = 'idle' | 'playing' | 'paused' | 'completed' | 'error';

export interface TTSPlayerOptions {
  /** 播放速率 (0.5 ~ 2.0) */
  rate?: number;
  /** 语音 ID */
  voiceId?: string;
  /** 播放完成回调 */
  onComplete?: () => void;
  /** 播放错误回调 */
  onError?: (error: string) => void;
}

/**
 * useTTSPlayer — TTS 播放控制 Hook
 *
 * 用法：
 *   const ttsPlayer = useTTSPlayer({ onComplete: nextAction });
 *   ttsPlayer.play("欢迎来到本次课程");
 *   ttsPlayer.pause();
 *   ttsPlayer.resume();
 */
export function useTTSPlayer(options: TTSPlayerOptions = {}) {
  const { rate = 1.0, voiceId, onComplete, onError } = options;

  const [state, setState] = useState<TTSPlayerState>('idle');
  const [currentActionId, setCurrentActionId] = useState<string | null>(null);
  const [progress, setProgress] = useState(0); // 0-100

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioUrlRef = useRef<string | null>(null);
  const isCancelledRef = useRef(false);
  // 播放序号：每次 play 递增，旧 play 的异步回调（synthesize 返回/audio 事件）
  // 通过序号比对识别「自己已被新 play 取代」，防止旧回调误推进播放链（双推进/双声道）
  const playSeqRef = useRef(0);

  // 清理音频资源
  const cleanupAudio = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.src = '';
      audioRef.current = null;
    }
    if (audioUrlRef.current) {
      URL.revokeObjectURL(audioUrlRef.current);
      audioUrlRef.current = null;
    }
    stopBrowserTTS();
  }, []);

  /**
   * 播放指定文本的 TTS
   * @param text 要朗读的文本
   * @param actionId 关联的 Action ID（用于状态跟踪）
   */
  const play = useCallback(
    async (text: string, actionId?: string) => {
      const seq = ++playSeqRef.current;

      if (!text?.trim()) {
        setState('completed');
        onComplete?.();
        return;
      }

      isCancelledRef.current = false;
      cleanupAudio();

      if (actionId) setCurrentActionId(actionId);
      setProgress(0);

      try {
        // 合成语音（音色优先级：options.voiceId > localStorage 学生选择 > 后端默认云希）
        const effectiveVoiceId = voiceId || localStorage.getItem('ttsVoiceId') || undefined;
        const result = await synthesize({
          text,
          voiceId: effectiveVoiceId,
          rate,
          language: 'zh-CN',
        });

        // 过期调用（已被 stop / 被新 play 取代）直接丢弃
        if (seq !== playSeqRef.current || isCancelledRef.current) return;

        // 如果没有音频 URL（浏览器原生 TTS），直接标记完成
        if (!result.audioUrl) {
          setState('completed');
          setProgress(100);
          onComplete?.();
          return;
        }

        // 创建 Audio 元素播放
        const audio = new Audio(result.audioUrl);
        audioRef.current = audio;
        audio.playbackRate = rate;

        // 事件绑定
        let playTimeout: ReturnType<typeof setTimeout> | null = null;
        const clearPlayTimeout = () => {
          if (playTimeout) { clearTimeout(playTimeout); playTimeout = null; }
        };

        audio.onended = () => {
          if (seq !== playSeqRef.current || isCancelledRef.current) return;
          clearPlayTimeout();
          setState('completed');
          setProgress(100);
          cleanupAudio();
          onComplete?.();
        };

        audio.onerror = (e: Event | string) => {
          if (seq !== playSeqRef.current || isCancelledRef.current) return;
          clearPlayTimeout();
          const errMsg = `音频播放失败: ${typeof e === 'string' ? e : (e as Event).type || 'unknown'}`;
          console.warn('[TTS]', errMsg);
          setState('error');
          cleanupAudio();
          onError?.(errMsg);
          // 出错后仍然触发完成，继续自动播放（优雅降级）
          onComplete?.();
        };

        audio.ontimeupdate = () => {
          if (result.durationMs > 0) {
            const currentProgress = Math.min(
              (audio.currentTime * 1000 / result.durationMs) * 100,
              100,
            );
            setProgress(currentProgress);
          }
        };

        // 播放超时兜底：音频开始播放（onplaying）或结束（onended）后取消；
        // 若 10s 内仍未开始播放（媒体加载挂起/play Promise 永久 pending），跳过该动作，保证播放链不卡死
        audio.onplaying = () => clearPlayTimeout();
        playTimeout = setTimeout(() => {
          if (seq !== playSeqRef.current || isCancelledRef.current) return;
          console.warn('[TTS] 播放超时（10s 未开始），跳过该动作');
          cleanupAudio();
          onComplete?.();
        }, 10000);

        setState('playing');
        try {
          await audio.play();
        } catch (err) {
          clearPlayTimeout();
          const e = err as DOMException;
          // 自动播放被浏览器阻止 → 用静音播放解锁（Chrome 允许 muted 自动播放），成功后恢复有声
          if (e.name === 'NotAllowedError') {
            console.warn('[TTS] 自动播放被阻止，尝试静音解锁播放');
            audio.muted = true;
            await audio.play();
            audio.muted = false;
            return;
          }
          throw err;
        }
      } catch (err) {
        if (seq !== playSeqRef.current || isCancelledRef.current) return;
        console.warn('[TTS] 播放失败:', err);
        setState('error');
        // 出错后仍继续播放链
        onComplete?.();
      }
    },
    [voiceId, rate, cleanupAudio, onComplete, onError],
  );

  /**
   * 暂停播放
   */
  const pause = useCallback(() => {
    // 作废进行中的 play（synthesize 可能仍在路上，返回后不得继续播放/推进）
    playSeqRef.current += 1;
    if (audioRef.current && !audioRef.current.paused) {
      audioRef.current.pause();
      setState('paused');
    }
    stopBrowserTTS();
  }, []);

  /**
   * 恢复播放
   */
  const resume = useCallback(() => {
    if (audioRef.current && audioRef.current.paused) {
      audioRef.current.play().catch(console.warn);
      setState('playing');
    }
  }, []);

  /**
   * 停止播放
   */
  const stop = useCallback(() => {
    isCancelledRef.current = true;
    playSeqRef.current += 1; // 作废进行中的 play
    cleanupAudio();
    setState('idle');
    setCurrentActionId(null);
    setProgress(0);
  }, [cleanupAudio]);

  /**
   * 预缓存文本列表的音频
   */
  const prefetch = useCallback(
    async (texts: string[]) => {
      await prefetchSceneAudios(texts, voiceId);
    },
    [voiceId],
  );

  // 组件卸载时清理
  useEffect(() => {
    return () => {
      isCancelledRef.current = true;
      cleanupAudio();
    };
  }, [cleanupAudio]);

  return {
    /** 播放器状态 */
    state,
    /** 当前播放的 Action ID */
    currentActionId,
    /** 播放进度 0-100 */
    progress,

    /** 播放 TTS */
    play,
    /** 暂停 */
    pause,
    /** 恢复 */
    resume,
    /** 停止 */
    stop,
    /** 预缓存音频 */
    prefetch,
  };
}

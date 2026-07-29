// ================================================================
// ttsService.ts — TTS 语音合成服务层
//
// 职责：
//   1. 调用后端 TTS API 合成语音
//   2. 浏览器原生 SpeechSynthesis fallback
//   3. 音频预缓存（进入场景时提前合成所有 speech）
// ================================================================
import { apiClient } from '../api/apiClient';
import type { ApiResponse } from '../api/types';

// ─── 类型定义 ───

export interface TTSRequest {
  text: string;
  voiceId?: string;
  rate?: number;
  language?: string;
}

export interface TTSResponse {
  audioUrl: string;
  durationMs: number;
  format: 'mp3' | 'wav' | 'ogg';
}

/**
 * 从后端 API 合成 TTS 语音
 * 后端需要实现 POST /api/tts/synthesize
 */
async function synthesizeAPI(request: TTSRequest): Promise<TTSResponse> {
  const res = await apiClient.post<ApiResponse<TTSResponse>>('/api/tts/synthesize', {
    text: request.text,
    voiceId: request.voiceId || 'default',
    rate: request.rate ?? 1.0,
    language: request.language || 'zh-CN',
  });

  if (!res.data?.data) {
    throw new Error('TTS API 返回数据异常');
  }
  return res.data.data;
}

/**
 * 浏览器原生 TTS 合成（fallback）
 * 使用 SpeechSynthesis API 朗读文本
 * 返回一个模拟的 TTSResponse（durationMs 为估算值）
 */
function synthesizeBrowser(text: string, rate = 1.0): Promise<TTSResponse> {
  return new Promise((resolve, reject) => {
    if (!window.speechSynthesis) {
      reject(new Error('浏览器不支持 SpeechSynthesis'));
      return;
    }

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'zh-CN';
    utterance.rate = rate;

    // 选择中文语音
    const voices = window.speechSynthesis.getVoices();
    const zhVoice = voices.find(
      (v) => v.lang.startsWith('zh') && v.localService,
    );
    if (zhVoice) utterance.voice = zhVoice;

    const startTime = Date.now();
    utterance.onend = () => {
      resolve({
        audioUrl: '',
        durationMs: Date.now() - startTime,
        format: 'wav',
      });
    };
    utterance.onerror = (e) => {
      reject(new Error(`SpeechSynthesis 错误: ${e.error}`));
    };

    // 某些浏览器需要延迟一下
    setTimeout(() => window.speechSynthesis.speak(utterance), 50);
  });
}

// ─── 缓存 ───

/** 文本 → AudioBuffer/audioUrl 的缓存 Map */
const audioCache = new Map<string, { audioUrl: string; durationMs: number }>();

/**
 * 合成语音
 * 先尝试后端 API，失败后自动降级到浏览器原生 TTS
 */
export async function synthesize(request: TTSRequest): Promise<TTSResponse> {
  // 缓存命中
  const cacheKey = `${request.voiceId || 'default'}:${request.text.slice(0, 100)}`;
  const cached = audioCache.get(cacheKey);
  if (cached) {
    return { ...cached, format: 'mp3' };
  }

  // 尝试后端 API
  try {
    const result = await synthesizeAPI(request);
    audioCache.set(cacheKey, {
      audioUrl: result.audioUrl,
      durationMs: result.durationMs,
    });
    return result;
  } catch (apiError) {
    console.warn('[TTS] API 合成失败，降级到浏览器原生 TTS:', apiError);
    // 降级到浏览器原生 TTS
    const result = await synthesizeBrowser(request.text, request.rate);
    audioCache.set(cacheKey, {
      audioUrl: result.audioUrl,
      durationMs: result.durationMs,
    });
    return result;
  }
}

/**
 * 预缓存场景中所有 speech 动作的音频
 * 在进入场景时调用，提前合成语音以避免播放时的等待
 */
export async function prefetchSceneAudios(
  texts: string[],
  voiceId?: string,
): Promise<void> {
  const uniqueTexts = [...new Set(texts)];
  const pending = uniqueTexts.filter((text) => {
    const key = `${voiceId || 'default'}:${text.slice(0, 100)}`;
    return !audioCache.has(key);
  });

  if (pending.length === 0) return;

  console.log(`[TTS] 预缓存 ${pending.length} 条语音...`);

  // 并发预取，但限制并发数为 3 避免 API 限流
  const concurrency = 3;
  for (let i = 0; i < pending.length; i += concurrency) {
    const batch = pending.slice(i, i + concurrency);
    await Promise.allSettled(
      batch.map((text) => synthesize({ text, voiceId }).catch(() => {})),
    );
  }

  console.log(`[TTS] 预缓存完成`);
}

/**
 * 清除 TTS 音频缓存
 */
export function clearTTSCache(): void {
  audioCache.clear();
}

/**
 * 停止所有浏览器原生 TTS 播放
 */
export function stopBrowserTTS(): void {
  if (window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
}

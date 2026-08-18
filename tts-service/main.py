# ============================================================
# EduMentor TTS 微服务 — 基于 Microsoft Edge TTS（edge-tts）
#
# 功能：
#   POST /synthesize  {text, voice, rate} → {audioUrl, durationMs, format}
#   GET  /voices      中文音色列表
#   GET  /health      健康检查
#   GET  /audio/{file} 已合成音频（StaticFiles）
#
# 引擎策略（音质优先）：
#   1. edge-tts（自然音色：云希/晓晓等）优先；
#   2. 不可用（微软服务拒绝/网络被墙）→ 自动降级 espeak-ng 离线合成；
#   3. 0 字节坏缓存自动清理重试，避免命中损坏音频。
# 服务器可通过 TTS_PROXY 环境变量为 edge-tts 配置 HTTP 代理。
#
# 缓存：本地磁盘（key = md5(text+voice+rate)），重复合成直接命中
# ============================================================
import asyncio
import hashlib
import os
import subprocess
import wave
from pathlib import Path

import edge_tts
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from mutagen.mp3 import MP3
from pydantic import BaseModel

# ── 配置 ──
AUDIO_DIR = Path(os.environ.get("TTS_AUDIO_DIR", "./audio"))
AUDIO_DIR.mkdir(parents=True, exist_ok=True)
HOST = os.environ.get("TTS_HOST", "0.0.0.0")
PORT = int(os.environ.get("TTS_PORT", "5080"))

# edge-tts 网络参数：TTS_PROXY 支持服务器走代理访问微软服务；
# 超时控制保证 edge-tts 挂起时能快速降级 espeak-ng
TTS_PROXY = (
    os.environ.get("TTS_PROXY")
    or os.environ.get("HTTPS_PROXY")
    or os.environ.get("https_proxy")
)
EDGE_CONNECT_TIMEOUT = int(os.environ.get("TTS_CONNECT_TIMEOUT", "10"))
EDGE_RECEIVE_TIMEOUT = int(os.environ.get("TTS_RECEIVE_TIMEOUT", "30"))

app = FastAPI(title="EduMentor TTS Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount("/audio", StaticFiles(directory=str(AUDIO_DIR)), name="audio")

# ── 中文音色库（默认男声云希；学生可在前端选择） ──
VOICES = [
    {"voiceId": "zh-CN-YunxiNeural",   "name": "云希（男 · 讲师）",   "gender": "男", "style": "沉稳清晰"},
    {"voiceId": "zh-CN-YunjianNeural", "name": "云健（男 · 新闻）",   "gender": "男", "style": "专业播报"},
    {"voiceId": "zh-CN-YunyangNeural", "name": "云扬（男 · 活力）",   "gender": "男", "style": "阳光热情"},
    {"voiceId": "zh-CN-YunxiaNeural",  "name": "云夏（男 · 少年）",   "gender": "男", "style": "青春明快"},
    {"voiceId": "zh-CN-XiaoxiaoNeural","name": "晓晓（女 · 温暖）",   "gender": "女", "style": "亲切柔和"},
    {"voiceId": "zh-CN-XiaoyiNeural",  "name": "晓伊（女 · 活泼）",   "gender": "女", "style": "灵动俏皮"},
    {"voiceId": "zh-CN-XiaomoNeural",  "name": "晓墨（女 · 知性）",   "gender": "女", "style": "温婉知性"},
    {"voiceId": "zh-CN-XiaohanNeural", "name": "晓涵（女 · 温柔）",   "gender": "女", "style": "轻柔舒缓"},
]
DEFAULT_VOICE = "zh-CN-YunxiNeural"

# ── 请求/响应模型 ──
class SynthesizeRequest(BaseModel):
    text: str
    voice: str = DEFAULT_VOICE
    rate: float = 0.95  # 0.5~2.0，edge-tts 用百分比


class SynthesizeResponse(BaseModel):
    audioUrl: str
    durationMs: int
    format: str = "mp3"
    engine: str = "edge-tts"


@app.get("/health")
async def health():
    return {"status": "UP", "service": "edge-tts"}


@app.get("/voices")
async def voices():
    return {"default": DEFAULT_VOICE, "voices": VOICES}


def _rate_to_edge(rate: float) -> str:
    """前端 rate(0.5~2.0) → edge-tts 百分比字符串（+x% / -x%）"""
    pct = int(round((rate - 1.0) * 100))
    return f"{pct:+d}%"


def _synthesize_espeak(text: str, out_wav: Path) -> Path:
    """espeak-ng 离线合成（edge-tts 不可用时的兜底引擎）。

    在无法访问微软 edge-tts 服务的环境（如国内云服务器）自动降级。
    音质为合成语音（单声道 wav），但保证课堂讲解能出声。
    """
    subprocess.run(
        ["espeak-ng", "-v", "zh", "-s", "155", "-w", str(out_wav), text],
        check=True, capture_output=True, timeout=120,
    )
    return out_wav


def _wav_duration_ms(path: Path) -> int:
    with wave.open(str(path), "rb") as w:
        return int(w.getnframes() / max(1, w.getframerate()) * 1000)


def _cache_path(text: str, voice: str, rate: float, suffix: str = "mp3") -> Path:
    key = hashlib.md5(f"{voice}|{rate}|{text}".encode("utf-8")).hexdigest()
    return AUDIO_DIR / f"{key}.{suffix}"


def _valid_audio(path: Path) -> bool:
    """音频缓存有效：文件存在且非空（拒绝 edge-tts 半途失败留下的 0 字节坏缓存）"""
    return path.exists() and path.stat().st_size > 0


@app.post("/synthesize")
async def synthesize(req: SynthesizeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text 不能为空")
    if len(text) > 2000:
        raise HTTPException(status_code=400, detail="text 过长（≤2000 字）")

    rate = max(0.5, min(2.0, req.rate))
    out = _cache_path(text, req.voice, rate)
    is_fallback = False

    # 缓存命中（含 espeak 兜底缓存）；0 字节坏缓存视为无效，删除后重新合成
    if not _valid_audio(out):
        if out.exists():
            out.unlink(missing_ok=True)
        try:
            await edge_tts.Communicate(
                text, req.voice,
                rate=_rate_to_edge(rate),
                proxy=TTS_PROXY,
                connect_timeout=EDGE_CONNECT_TIMEOUT,
                receive_timeout=EDGE_RECEIVE_TIMEOUT,
            ).save(str(out))
            if not _valid_audio(out):
                # edge-tts 生成了空文件 → 视为失败，走降级
                out.unlink(missing_ok=True)
                raise RuntimeError("edge-tts 生成空文件")
        except Exception:
            # 清理 edge-tts 异常时可能留下的 0 字节坏文件
            out.unlink(missing_ok=True)
            # edge-tts 不可用（微软服务拒绝/无法访问）→ 降级 espeak-ng 离线合成
            out = _cache_path(text, f"{req.voice}:espeak", rate, suffix="wav")
            if not _valid_audio(out):
                if out.exists():
                    out.unlink(missing_ok=True)
                try:
                    _synthesize_espeak(text, out)
                except Exception as e2:
                    raise HTTPException(status_code=502, detail=f"TTS 合成失败（edge-tts 与 espeak-ng 均不可用）: {e2}")
                if not _valid_audio(out):
                    raise HTTPException(status_code=502, detail="TTS 合成结果为空文件")
            is_fallback = True

    try:
        if out.suffix.lower() == ".wav":
            duration_ms = _wav_duration_ms(out)
        else:
            duration_ms = int(MP3(str(out)).info.length * 1000)
    except Exception:
        # 读时长失败时按字数估算（约 4.5 字/秒 @0.95x）
        duration_ms = int(len(text) / 4.5 * 1000)

    fmt = "wav" if out.suffix.lower() == ".wav" else "mp3"
    return SynthesizeResponse(
        audioUrl=f"/audio/{out.name}",
        durationMs=max(duration_ms, 500),
        format=fmt,
        engine="espeak-ng" if is_fallback else "edge-tts",
    )


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)


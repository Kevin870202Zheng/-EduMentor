# ============================================================
# EduMentor TTS 微服务 — 基于 Microsoft Edge TTS（edge-tts）
#
# 功能：
#   POST /synthesize  {text, voice, rate} → {audioUrl, durationMs, format}
#   GET  /voices      中文音色列表
#   GET  /health      健康检查
#   GET  /audio/{file} 已合成音频（StaticFiles）
#
# 缓存：本地磁盘（key = md5(text+voice+rate)），重复合成直接命中
# ============================================================
import asyncio
import hashlib
import os
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


def _cache_path(text: str, voice: str, rate: float) -> Path:
    key = hashlib.md5(f"{voice}|{rate}|{text}".encode("utf-8")).hexdigest()
    return AUDIO_DIR / f"{key}.mp3"


@app.post("/synthesize")
async def synthesize(req: SynthesizeRequest):
    text = (req.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text 不能为空")
    if len(text) > 2000:
        raise HTTPException(status_code=400, detail="text 过长（≤2000 字）")

    rate = max(0.5, min(2.0, req.rate))
    out = _cache_path(text, req.voice, rate)

    # 缓存命中
    if not out.exists():
        try:
            await edge_tts.Communicate(
                text, req.voice, rate=_rate_to_edge(rate)
            ).save(str(out))
        except Exception as e:
            raise HTTPException(status_code=502, detail=f"edge-tts 合成失败: {e}")

    try:
        duration_ms = int(MP3(str(out)).info.length * 1000)
    except Exception:
        # 读时长失败时按字数估算（约 4.5 字/秒 @0.95x）
        duration_ms = int(len(text) / 4.5 * 1000)

    return SynthesizeResponse(
        audioUrl=f"/audio/{out.name}",
        durationMs=max(duration_ms, 500),
        format="mp3",
    )


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)


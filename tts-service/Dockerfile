FROM python:3.12-slim

WORKDIR /app

# espeak-ng：edge-tts（微软在线服务）不可用时的离线兜底 TTS 引擎
RUN apt-get update && apt-get install -y --no-install-recommends espeak-ng \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

COPY main.py .

ENV TTS_AUDIO_DIR=/app/audio
ENV TTS_PORT=5080

EXPOSE 5080

CMD ["python", "main.py"]


FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

ENV TTS_AUDIO_DIR=/app/audio
ENV TTS_PORT=5080

EXPOSE 5080

CMD ["python", "main.py"]


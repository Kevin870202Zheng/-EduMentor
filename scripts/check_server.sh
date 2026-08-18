#!/bin/bash
# 验证远端 TTS 完整链路（synthesize + 音频下载 + content-type）
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== 等待后端健康 ==="; for i in $(seq 1 40); do code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo 000); if [ "$code" = "200" ]; then echo "healthy"; break; fi; sleep 3; done; TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student01\",\"password\":\"student123\"}" 2>/dev/null | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])") && echo "=== synthesize ===" && RESP=$(curl -s -m 60 -X POST http://localhost:8080/api/tts/synthesize -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "{\"text\":\"罪刑法定原则是刑法的基本原则\",\"voiceId\":\"zh-CN-YunxiNeural\"}") && echo "$RESP" && URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"audioUrl\"])") && echo "=== 音频下载: $URL ===" && curl -s -o /tmp/v.bin -w "HTTP %{http_code}, type %{content_type}, size %{size_download}\n" "http://localhost:8080$URL" -H "Authorization: Bearer $TOKEN" && xxd /tmp/v.bin | head -1'

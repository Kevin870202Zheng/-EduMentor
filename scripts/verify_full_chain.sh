#!/bin/bash
# 确认坏缓存清理 + 后端完整链路验证
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== VOLUME 文件统计 ==="; docker exec edumentor-tts sh -c "ls -la /app/audio | awk \"{print \\\$5, \\\$9}\" | sort -n | head -20"; echo "=== 0 字节文件数 ==="; docker exec edumentor-tts sh -c "find /app/audio -type f -size 0 | wc -l"; echo "=== 后端完整链路 ==="; TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student01\",\"password\":\"student123\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])") && RESP=$(curl -s -m 90 -X POST http://localhost:8080/api/tts/synthesize -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "{\"text\":\"罪刑法定原则是刑法的基本原则\",\"voiceId\":\"zh-CN-YunxiNeural\"}") && echo "$RESP" && URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"audioUrl\"])") && curl -s -o /tmp/v.bin -w "audio HTTP %{http_code} type %{content_type} size %{size_download}\n" "http://localhost:8080$URL" -H "Authorization: Bearer $TOKEN"' 2>&1 | tail -35

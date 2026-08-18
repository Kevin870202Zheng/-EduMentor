#!/bin/bash
# 远端直连 tts-service 测试 + 检查音频文件
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== AUDIO FILES ==="; docker exec edumentor-tts sh -c "ls -la /app/audio | head -30"; echo "=== DIRECT SYNTHESIZE TEST ==="; RESP=$(curl -s -m 90 -X POST http://localhost:5080/synthesize -H "Content-Type: application/json" -d "{\"text\":\"直接测试tts服务合成音频\",\"voice\":\"zh-CN-YunxiNeural\",\"rate\":0.95}"); echo "$RESP"; URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"audioUrl\"])" 2>/dev/null); echo "=== DOWNLOAD ==="; curl -s -o /tmp/t.wav -w "HTTP %{http_code} type %{content_type} size %{size_download}\n" "http://localhost:5080$URL"' 2>&1 | tail -40

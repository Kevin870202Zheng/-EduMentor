#!/bin/bash
# 查看远端 edumentor-tts 容器日志与配置
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== TTS LOG ==="; docker logs edumentor-tts --tail 40 2>&1 | tail -40; echo "=== TTS ENV ==="; docker exec edumentor-tts env 2>/dev/null | grep -i -E "azure|tts|key|endpoint|region|port" | sed "s/\(KEY\|SECRET\|TOKEN\)=.*/\1=***/"; echo "=== TTS FILES ==="; docker exec edumentor-tts sh -c "ls -la /app 2>/dev/null; cat /app/.env 2>/dev/null | sed s/KEY=.*/KEY=***/" 2>&1 | head -30' 2>&1 | tail -80

#!/bin/bash
# 清理容器内 0 字节坏缓存（$() 在容器内执行）
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker exec edumentor-tts sh -c '\''BEFORE=$(find /app/audio -type f | wc -l); find /app/audio -type f -size 0 -delete; AFTER=$(find /app/audio -type f | wc -l); echo "removed $((BEFORE-AFTER)) bad files, remaining $AFTER"'\''; echo "=== 目录现状 ==="; docker exec edumentor-tts ls -la /app/audio | head -15'

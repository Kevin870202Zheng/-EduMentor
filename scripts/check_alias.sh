#!/bin/bash
# 查看后端容器 TTS 相关日志 + tts 容器网络别名
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== BACKEND TTS LOG ==="; docker logs edumentor-app --tail 60 2>&1 | grep -i -E "tts|UnknownHost|NoSuchElement|synthesize" | tail -12; echo "=== TTS NETWORK ALIAS ==="; docker inspect edumentor-tts --format "{{json .NetworkSettings.Networks.edumentor_network.Aliases}}"; echo "=== 容器内解析测试 ==="; docker exec edumentor-app sh -c "getent hosts tts-service || echo DNS_FAIL"' 2>&1 | tail -20

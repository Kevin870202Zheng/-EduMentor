#!/bin/bash
# 查看远端 tts-service 容器与 compose 配置
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"; echo "=== COMPOSE ==="; cat /home/ubuntu/EduMentor/docker-compose.yml 2>/dev/null; echo "=== TTS SERVICE LOG ==="; docker logs tts-service --tail 25 2>&1 | tail -25' 2>&1 | tail -80

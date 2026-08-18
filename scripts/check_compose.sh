#!/bin/bash
# 查看远端 compose 前半部分（services 定义）
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"; echo "=== COMPOSE HEAD ==="; sed -n "1,90p" /home/ubuntu/EduMentor/docker-compose.yml 2>/dev/null; echo "=== COMPOSE TTS GREP ==="; grep -n -i -A12 "tts" /home/ubuntu/EduMentor/docker-compose.yml 2>/dev/null' 2>&1 | tail -120

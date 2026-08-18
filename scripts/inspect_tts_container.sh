#!/bin/bash
# 查看远端 edumentor-tts 容器启动方式（mount/network/启动命令）
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker inspect edumentor-tts --format "{{json .HostConfig.Binds}}|{{json .HostConfig.NetworkMode}}|{{json .Config.Image}}|{{json .HostConfig.PortBindings}}"; echo "=== compose files ==="; ls -la /home/ubuntu/EduMentor/*.yml /home/ubuntu/EduMentor/*.yaml 2>/dev/null; echo "=== tts dir ==="; ls -la /home/ubuntu/EduMentor/tts-service 2>/dev/null | head -20' 2>&1 | tail -30

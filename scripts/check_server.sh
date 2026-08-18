#!/bin/bash
# 查看服务器 backend-java 与容器配置
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== backend-java 目录 ==="; ls -la /home/ubuntu/EduMentor/backend-java/ 2>/dev/null | head -20; echo "=== Dockerfile ==="; cat /home/ubuntu/EduMentor/backend-java/Dockerfile 2>/dev/null; echo "=== 容器内 jar ==="; docker exec edumentor-app sh -c "ls -lh /app/*.jar 2>/dev/null; ps aux | grep java | grep -v grep | head -2" 2>/dev/null'

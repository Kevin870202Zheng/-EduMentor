#!/bin/bash
# 查看远端前端容器状态与代码版本
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== 容器状态 ==="; docker ps --filter name=edumentor-frontend --format "{{.Names}} {{.Status}} {{.Image}}"; echo "=== 镜像信息 ==="; docker inspect edumentor-frontend --format "Created: {{.Created}}"; echo "=== 镜像创建时间 ==="; docker image inspect edumentor-frontend --format "Created: {{.Created}}"; echo "=== 前端容器内文件时间 ==="; docker exec edumentor-frontend sh -c "ls -la /usr/share/nginx/html/ 2>/dev/null | head; ls -la /app 2>/dev/null | head" 2>&1 | head -20' 2>&1 | tail -25

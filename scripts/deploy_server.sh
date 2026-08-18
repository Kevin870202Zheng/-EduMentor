#!/bin/bash
# EduMentor 部署脚本：传输构建产物到服务器并更新容器
# 用法: SERVER_PASS='xxx' bash scripts/deploy_server.sh
set -e

SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
LOCAL_ROOT="/Users/roosevelt/vscode/EduMentor"
REMOTE_DIR="/home/ubuntu/EduMentor"

if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
SSH="sshpass -p $SERVER_PASS ssh -o StrictHostKeyChecking=no -o ConnectTimeout=20 $SERVER_USER@$SERVER_HOST"
SCP="sshpass -p $SERVER_PASS scp -o StrictHostKeyChecking=no -o ConnectTimeout=20"

echo "=== [1/4] 传输后端 jar (111M) ==="
$SCP "$LOCAL_ROOT/backend-java/target/edumentor-backend-1.0.0.jar" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/edumentor-backend.jar"
echo "后端 jar 传输完成"

echo "=== [2/4] 传输前端 dist ==="
$SCP -r "$LOCAL_ROOT/frontend/dist" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/frontend-dist"
echo "前端 dist 传输完成"

echo "=== [3/4] 更新容器 ==="
$SSH "
  set -e
  echo '--- 替换后端 jar ---'
  docker cp $REMOTE_DIR/edumentor-backend.jar edumentor-app:/app/app.jar
  echo '--- 替换前端静态文件 ---'
  docker cp $REMOTE_DIR/frontend-dist/. edumentor-frontend:/usr/share/nginx/html/
  echo '--- 重启容器 ---'
  docker restart edumentor-app
  docker restart edumentor-frontend
  echo '--- 等待后端健康 ---'
  for i in \$(seq 1 30); do
    code=\$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null || echo 000)
    if [ \"\$code\" = \"200\" ]; then echo \"后端健康 OK\"; break; fi
    sleep 3
  done
  curl -s http://localhost:8080/actuator/health 2>/dev/null | head -c 200
  echo ''
  echo '--- 容器状态 ---'
  docker ps --format '{{.Names}}\t{{.Status}}'
"

echo "=== [4/4] 前端验证 ==="
sleep 2
$SSH "curl -s -o /dev/null -w '前端 HTTP %{http_code}\n' http://localhost:3000/ 2>/dev/null"
echo "=== 部署完成 ==="

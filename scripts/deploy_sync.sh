#!/bin/bash
# EduMentor 部署更新 + 数据同步脚本
# 用法: SERVER_PASS='xxx' bash scripts/deploy_sync.sh
set -e

SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
LOCAL_ROOT="/Users/roosevelt/vscode/EduMentor"
REMOTE_DIR="/home/ubuntu/EduMentor"
DUMP_FILE="/tmp/edumentor_dev_sync.dump"

if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
SSH="sshpass -p $SERVER_PASS ssh -o StrictHostKeyChecking=no -o ConnectTimeout=20 $SERVER_USER@$SERVER_HOST"
SCP="sshpass -p $SERVER_PASS scp -o StrictHostKeyChecking=no -o ConnectTimeout=20"

echo "=== [1/5] 本地导出数据库 ==="
PGPASSWORD='dev@123' pg_dump -h localhost -U edumentor_dev -d edumentor_dev -Fc -f "$DUMP_FILE"
ls -lh "$DUMP_FILE"

echo "=== [2/5] 传输后端 jar + 数据库 dump ==="
$SCP "$LOCAL_ROOT/backend-java/target/edumentor-backend-1.0.0.jar" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/edumentor-backend.jar"
$SCP "$DUMP_FILE" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/edumentor_dev_sync.dump"
echo "传输完成"

echo "=== [3/5] 更新后端容器 ==="
$SSH "docker cp $REMOTE_DIR/edumentor-backend.jar edumentor-app:/app/app.jar && docker restart edumentor-app"
echo "后端容器已更新"

echo "=== [4/5] 远程恢复数据库（全量覆盖，以本地为准） ==="
$SSH "
  set -e
  echo '--- 停后端（避免写入冲突） ---'
  docker stop edumentor-app >/dev/null 2>&1 || true
  echo '--- 恢复数据 ---'
  docker exec -i edumentor-postgres pg_restore -U edumentor -d edumentor --clean --if-exists --no-owner < $REMOTE_DIR/edumentor_dev_sync.dump 2>&1 | tail -5 || true
  echo '--- 启动后端 ---'
  docker start edumentor-app >/dev/null 2>&1 || true
  echo '--- 验证数据 ---'
  docker exec edumentor-postgres psql -U edumentor -d edumentor -t -c 'SELECT count(*) FROM classrooms;' 2>/dev/null
  docker exec edumentor-postgres psql -U edumentor -d edumentor -t -c 'SELECT count(*) FROM courses;' 2>/dev/null
"

echo "=== [5/5] 等待后端健康 ==="
$SSH "
  for i in \$(seq 1 30); do
    code=\$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null || echo 000)
    if [ \"\$code\" = \"200\" ]; then echo '后端健康 OK'; break; fi
    sleep 3
  done
  echo '--- 容器状态 ---'
  docker ps --format '{{.Names}}\t{{.Status}}'
"
echo "=== 全部完成 ==="

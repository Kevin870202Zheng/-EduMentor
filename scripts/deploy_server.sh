#!/bin/bash
# 服务器部署脚本 - 在服务器上执行
set -e

echo "=== 1. 停止旧进程 ==="
pkill -9 -f java 2>/dev/null || true
sleep 2

echo "=== 2. 启动后端 ==="
cd /home/ubuntu/EduMentor/backend-java

DATABASE_URL=jdbc:postgresql://172.18.0.2:5432/edumentor \
DATABASE_USERNAME=edumentor \
DATABASE_PASSWORD=REPLACED_DB_PASSWORD \
JWT_SECRET_KEY=edumentor-dev-secret-key-minimum-256-bits-please-replace-in-production \
SPRING_PROFILES_ACTIVE=prod \
CORS_ALLOWED_ORIGINS=http://82.156.203.220:3000 \
LLM_PROVIDER=mock \
nohup java -Xmx4g -jar edumentor-backend-1.0.0.jar \
  --spring.flyway.enabled=false \
  --spring.jpa.hibernate.ddl-auto=none \
  > /tmp/edumentor-backend.log 2>&1 &

BGPID=$!
echo "后端PID: $BGPID"

echo "=== 3. 等待启动 ==="
for i in $(seq 1 30); do
  sleep 2
  if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 后端启动成功！"
    curl -s http://localhost:8080/actuator/health
    echo ""
    break
  fi
  if [ $i -eq 30 ]; then
    echo "❌ 启动超时"
    tail -20 /tmp/edumentor-backend.log
    exit 1
  fi
  echo -n "."
done

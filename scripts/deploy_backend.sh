#!/bin/bash
# 部署后端 jar + 重启 + 完整链路验证（含 wav 下载）
set -e
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
JAR="/Users/roosevelt/vscode/EduMentor/backend-java/target/edumentor-backend-1.0.0.jar"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi

echo "=== 1. 上传 jar ==="
sshpass -p "$SERVER_PASS" scp -o StrictHostKeyChecking=no "$JAR" "$SERVER_USER@$SERVER_HOST:/home/ubuntu/EduMentor/edumentor-backend.jar"

echo "=== 2. 重启后端 ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker cp /home/ubuntu/EduMentor/edumentor-backend.jar edumentor-app:/app/app.jar && docker restart edumentor-app >/dev/null && echo restarted'

echo "=== 3. 等待健康 + 完整链路验证 ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'for i in $(seq 1 40); do code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo 000); if [ "$code" = "200" ]; then echo healthy; break; fi; sleep 3; done; TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student01\",\"password\":\"student123\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])") && RESP=$(curl -s -m 90 -X POST http://localhost:8080/api/tts/synthesize -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "{\"text\":\"罪刑法定原则是刑法的基本原则\",\"voiceId\":\"zh-CN-YunxiNeural\"}") && echo "synthesize: $RESP" && URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"audioUrl\"])") && curl -s -o /tmp/v.bin -w "audio HTTP %{http_code} type %{content_type} size %{size_download}\n" "http://localhost:8080$URL" -H "Authorization: Bearer $TOKEN" && xxd /tmp/v.bin | head -1'

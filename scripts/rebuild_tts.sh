#!/bin/bash
# 重建 tts-service 容器（补回 network alias）+ 完整链路验证
set -e
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== 重建容器 ==="; docker rm -f edumentor-tts >/dev/null 2>&1; docker run -d --name edumentor-tts --network edumentor_network --network-alias tts-service -v edumentor_tts_audio:/app/audio -p 5080:5080 --restart unless-stopped edumentor-tts && sleep 3; echo "=== 别名确认 ==="; docker inspect edumentor-tts --format "{{json .NetworkSettings.Networks.edumentor_network.Aliases}}"; echo "=== 后端解析 ==="; docker exec edumentor-app sh -c "getent hosts tts-service || echo DNS_FAIL"; echo "=== 后端完整链路 ==="; for i in $(seq 1 20); do code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo 000); if [ "$code" = "200" ]; then break; fi; sleep 2; done; TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"student01\",\"password\":\"student123\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])") && RESP=$(curl -s -m 90 -X POST http://localhost:8080/api/tts/synthesize -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "{\"text\":\"罪刑法定原则是刑法的基本原则\",\"voiceId\":\"zh-CN-YunxiNeural\"}") && echo "$RESP" && URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"audioUrl\"])") && curl -s -o /tmp/v.bin -w "audio HTTP %{http_code} type %{content_type} size %{size_download}\n" "http://localhost:8080$URL" -H "Authorization: Bearer $TOKEN" && xxd /tmp/v.bin | head -1'

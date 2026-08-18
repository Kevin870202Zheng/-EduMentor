#!/bin/bash
# 部署修复后的 tts-service 到远端：构建镜像 → 重建容器 → 清理 0 字节坏缓存 → 验证
set -e
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
LOCAL_DIR="/Users/roosevelt/vscode/EduMentor/tts-service"
REMOTE_DIR="/home/ubuntu/EduMentor/tts-service"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi

echo "=== 1. 上传 main.py ==="
sshpass -p "$SERVER_PASS" scp -o StrictHostKeyChecking=no "$LOCAL_DIR/main.py" "$SERVER_USER@$SERVER_HOST:$REMOTE_DIR/main.py"

echo "=== 2. 远端构建镜像 ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" \
  "cd $REMOTE_DIR && docker build -t edumentor-tts . 2>&1 | tail -3"

echo "=== 3. 重建容器 ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" \
  'docker rm -f edumentor-tts >/dev/null 2>&1; docker run -d --name edumentor-tts --network edumentor_network --network-alias tts-service -v edumentor_tts_audio:/app/audio -p 5080:5080 --restart unless-stopped edumentor-tts && sleep 3 && docker ps --filter name=edumentor-tts --format "{{.Names}} {{.Status}} {{.Ports}}"'

echo "=== 4. 清理 0 字节坏缓存 ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" \
  'docker exec edumentor-tts find /app/audio -type f -size 0 -delete && echo "bad cache cleaned"'

echo "=== 5. 验证 synthesize ==="
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" \
  'for i in $(seq 1 20); do code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5080/health 2>/dev/null || echo 000); if [ "$code" = "200" ]; then echo "tts healthy"; break; fi; sleep 2; done; RESP=$(curl -s -m 90 -X POST http://localhost:5080/synthesize -H "Content-Type: application/json" -d "{\"text\":\"罪刑法定原则是刑法的基本原则\",\"voice\":\"zh-CN-YunxiNeural\",\"rate\":0.95}"); echo "$RESP"; URL=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)[\"audioUrl\"])"); echo "--- download ---"; curl -s -o /tmp/v.bin -w "HTTP %{http_code} type %{content_type} size %{size_download}\n" "http://localhost:5080$URL"'

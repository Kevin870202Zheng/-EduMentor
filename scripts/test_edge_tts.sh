#!/bin/bash
# 测试服务器上 edge-tts 直连可用性 + 检查代理环境
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'echo "=== 宿主代理 env ==="; env | grep -i -E "proxy" || echo "no proxy"; echo "=== edge-tts 直连测试 ==="; docker exec edumentor-tts sh -c "cd /tmp && timeout 60 python3 -c \"import asyncio,edge_tts; asyncio.run(edge_tts.Communicate(chr(27979)+chr(35797), \\\"zh-CN-YunxiNeural\\\").save(\\\"/tmp/e.mp3\\\"))\" 2>&1 | tail -5; ls -la /tmp/e.mp3 2>/dev/null"' 2>&1 | tail -15

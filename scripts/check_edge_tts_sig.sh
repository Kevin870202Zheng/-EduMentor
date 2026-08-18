#!/bin/bash
# 查看 edge-tts 版本与 Communicate 签名
SERVER_HOST="82.156.203.220"
SERVER_USER="ubuntu"
if [ -z "$SERVER_PASS" ]; then
  echo "ERROR: 请设置 SERVER_PASS 环境变量"
  exit 1
fi
sshpass -p "$SERVER_PASS" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER_USER@$SERVER_HOST" \
  'docker exec edumentor-tts python3 -c "import edge_tts, inspect; print(\"version:\", edge_tts.__version__ if hasattr(edge_tts, \"__version__\") else \"n/a\"); print(inspect.signature(edge_tts.Communicate.__init__))" 2>&1' 2>&1 | tail -10

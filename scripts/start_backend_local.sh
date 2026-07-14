#!/bin/bash
# =============================================================================
# EduMentor 后端 — 本地开发启动脚本
# =============================================================================
# 使用方法: chmod +x scripts/start_backend_local.sh && ./scripts/start_backend_local.sh
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR/backend-java" || { echo "❌ 请从项目根目录运行"; exit 1; }

JAR_PATH="target/edumentor-backend-1.0.0.jar"
JAVA_HOME="/Users/roosevelt/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java"

# 从 .env 加载环境变量
if [ -f ".env" ]; then
  echo "📦 加载 .env 配置..."
  export $(grep -v '^#' .env | xargs)
fi

# 检查 JAR 是否存在
if [ ! -f "$JAR_PATH" ]; then
  echo "🔨 JAR 不存在，请先执行: cd backend-java && ./mvnw clean package -DskipTests"
  exit 1
fi

# 杀死已有的后端进程
PID=$(lsof -ti:8080 2>/dev/null)
if [ -n "$PID" ]; then
  echo "🔄 停止已有后端进程 (PID: $PID)..."
  kill "$PID" 2>/dev/null && sleep 2
fi

echo "🚀 启动 EduMentor 后端 (profile: dev)..."
nohup "$JAVA_HOME" -jar "$JAR_PATH" --spring.profiles.active=dev > /tmp/edumentor-backend.log 2>&1 &
BPID=$!
echo "✅ 后端已启动 (PID: $BPID)"
echo "📄 日志: tail -f /tmp/edumentor-backend.log"
echo "🌐 API: http://localhost:8080/api"

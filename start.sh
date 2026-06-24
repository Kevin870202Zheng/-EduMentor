#!/bin/bash

# ============================================================
#  智学导师 EduMentor — 一键启动脚本 (Java Spring Boot 版)
#  首届全国高师院校教师教学智能体应用创新设计大赛
#  赛道二：学习赋能智能体
# ============================================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend-java"
FRONTEND_DIR="$PROJECT_DIR/frontend"

# 默认端口
BACKEND_PORT=8080
FRONTEND_PORT=3001

# ---------- 工具函数 ----------
print_banner() {
    echo -e "${CYAN}"
    echo '╔══════════════════════════════════════════════════════════╗'
    echo '║       智学导师 EduMentor  v1.0.0                        ║'
    echo '║       学习赋能AI教育智能体                                ║'
    echo '║                                                        ║'
    echo '║       首届全国高师院校教师教学智能体                       ║'
    echo '║       应用创新设计大赛                                   ║'
    echo '╚══════════════════════════════════════════════════════════╝'
    echo -e "${NC}"
}

step()  { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} ${GREEN}→${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
error() { echo -e "${RED}[✗]${NC} $1"; }
success() { echo -e "${GREEN}[✓]${NC} $1"; }

# 查找可用端口
find_port() {
    local start_port=$1
    local port=$start_port
    while lsof -i :$port > /dev/null 2>&1; do
        port=$((port + 1))
        if [ $port -gt $((start_port + 50)) ]; then
            error "在 $start_port-$((start_port+50)) 范围内未找到可用端口"
            return 1
        fi
    done
    echo $port
    return 0
}

# ---------- 检查依赖 ----------
check_dependencies() {
    local missing=0
    for cmd in java mvn node npm curl; do
        if command -v $cmd &> /dev/null; then
            success "已找到 $cmd"
        else
            warn "未找到 $cmd，某些功能可能不可用"
            missing=$((missing + 1))
        fi
    done

    if command -v java &> /dev/null; then
        java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$java_version" -lt 21 ] 2>/dev/null; then
            warn "建议使用 JDK 21+，当前版本: $(java -version 2>&1 | head -1)"
        fi
    fi
    return $missing
}

# ---------- 启动后端 (Spring Boot) ----------
start_backend() {
    step "正在启动后端服务 (Spring Boot)..."

    # 查找可用端口
    PORT=$(find_port $BACKEND_PORT) || return 1
    if [ "$PORT" != "$BACKEND_PORT" ]; then
        warn "端口 $BACKEND_PORT 被占用，改用端口 $PORT"
        BACKEND_PORT=$PORT
    fi

    cd "$BACKEND_DIR"

    # 检查是否已有构建产物
    if [ ! -f "target/*.jar" ] && [ ! -d "target/classes" ]; then
        step "首次启动，编译打包中（可能需要 2-5 分钟）..."
        ./mvnw clean package -DskipTests -q 2>> "$PROJECT_DIR/logs/setup.log"
        if [ $? -ne 0 ]; then
            # 尝试使用系统 mvn
            step "Maven Wrapper 不可用，尝试系统 Maven..."
            mvn clean package -DskipTests -q 2>> "$PROJECT_DIR/logs/setup.log"
            if [ $? -ne 0 ]; then
                error "Maven 编译失败，请查看日志: tail -20 logs/setup.log"
                return 1
            fi
        fi
        success "编译打包完成"
    fi

    # 启动后端 (开发模式使用 spring-boot:run)
    step "启动后端服务 (端口 $BACKEND_PORT)..."
    SPRING_PROFILES_ACTIVE=dev nohup ./mvnw spring-boot:run -q > "$PROJECT_DIR/logs/backend.log" 2>&1 &
    local pid=$!
    echo $pid > "$PROJECT_DIR/.backend.pid"

    # 等待启动
    sleep 10
    if curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1; then
        success "后端服务启动成功 → http://localhost:$BACKEND_PORT"
    else
        warn "后端启动中，请稍候检查: tail -f logs/backend.log"
        sleep 10
        if curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1; then
            success "后端服务启动成功 → http://localhost:$BACKEND_PORT"
        else
            error "后端启动失败，请查看日志: tail -20 logs/backend.log"
            tail -5 "$PROJECT_DIR/logs/backend.log"
            return 1
        fi
    fi
    return 0
}

# ---------- 启动前端 ----------
start_frontend() {
    step "正在启动前端服务..."

    # 查找可用端口
    PORT=$(find_port $FRONTEND_PORT) || return 1
    if [ "$PORT" != "$FRONTEND_PORT" ]; then
        warn "端口 $FRONTEND_PORT 被占用，改用端口 $PORT"
        FRONTEND_PORT=$PORT
    fi

    cd "$FRONTEND_DIR"

    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        step "安装前端依赖（首次启动可能需要 1-2 分钟）..."
        npm install --legacy-peer-deps 2>> "$PROJECT_DIR/logs/setup.log" | tail -3
        if [ $? -ne 0 ]; then
            error "npm install 失败"
            return 1
        fi
        success "前端依赖安装完成"
    fi

    # 启动前端
    step "启动前端服务 (端口 $FRONTEND_PORT)..."
    nohup npx vite --host 0.0.0.0 --port $FRONTEND_PORT > "$PROJECT_DIR/logs/frontend.log" 2>&1 &
    local pid=$!
    echo $pid > "$PROJECT_DIR/.frontend.pid"

    # 等待启动
    sleep 5
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$FRONTEND_PORT 2>/dev/null)
    if [ "$http_code" = "200" ]; then
        success "前端服务启动成功 → http://localhost:$FRONTEND_PORT"
    else
        warn "前端编译中，再稍等片刻..."
        sleep 8
        http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$FRONTEND_PORT 2>/dev/null)
        if [ "$http_code" = "200" ]; then
            success "前端服务启动成功 → http://localhost:$FRONTEND_PORT"
        else
            warn "前端启动中，请查看: tail -f logs/frontend.log"
        fi
    fi
    return 0
}

# ---------- 停止服务 ----------
stop_services() {
    echo ""
    step "正在停止所有服务..."

    if [ -f "$PROJECT_DIR/.backend.pid" ]; then
        kill $(cat "$PROJECT_DIR/.backend.pid") 2>/dev/null && success "后端服务已停止" || warn "后端进程已不存在"
        rm -f "$PROJECT_DIR/.backend.pid"
    fi

    if [ -f "$PROJECT_DIR/.frontend.pid" ]; then
        kill $(cat "$PROJECT_DIR/.frontend.pid") 2>/dev/null && success "前端服务已停止" || warn "前端进程已不存在"
        rm -f "$PROJECT_DIR/.frontend.pid"
    fi

    # 清理残留 Java 进程
    lsof -ti :$BACKEND_PORT 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti :$FRONTEND_PORT 2>/dev/null | xargs kill -9 2>/dev/null || true

    echo ""
    success "所有服务已停止"
}

# ---------- 显示状态 ----------
show_status() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${GREEN}智学导师 EduMentor — 服务状态${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1; then
        echo -e "  ${GREEN}✅ 后端 API${NC}  →  http://localhost:$BACKEND_PORT"
    else
        echo -e "  ${RED}❌ 后端 API${NC}  →  未运行"
    fi

    local code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$FRONTEND_PORT 2>/dev/null)
    if [ "$code" = "200" ]; then
        echo -e "  ${GREEN}✅ 前端应用${NC}  →  http://localhost:$FRONTEND_PORT"
    else
        echo -e "  ${RED}❌ 前端应用${NC}  →  未运行"
    fi

    echo ""
    echo -e "  ${YELLOW}学生端${NC}:  http://localhost:$FRONTEND_PORT/student/dashboard"
    echo -e "  ${YELLOW}教师端${NC}:  http://localhost:$FRONTEND_PORT/teacher/dashboard"
    echo -e "  ${YELLOW}API${NC}:     http://localhost:$BACKEND_PORT/api/v1"
    echo -e "  ${YELLOW}Swagger${NC}: http://localhost:$BACKEND_PORT/swagger-ui.html"
    echo ""
    echo -e "  ${BLUE}测试账号${NC}:  student1 / 123456  (学生)"
    echo -e "  ${BLUE}         teacher1 / 123456  (教师)"
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ---------- 主流程 ----------
main() {
    print_banner

    # 创建日志目录
    mkdir -p "$PROJECT_DIR/logs"

    case "${1:-}" in
        stop)
            stop_services
            exit 0
            ;;
        restart)
            stop_services
            sleep 2
            main
            exit 0
            ;;
        status)
            show_status
            exit 0
            ;;
        help|--help|-h)
            echo "用法: ./start.sh [命令]"
            echo ""
            echo "命令:"
            echo "  (无)     启动所有服务"
            echo "  stop     停止所有服务"
            echo "  restart  重启所有服务"
            echo "  status   查看服务状态"
            echo "  help     显示帮助信息"
            exit 0
            ;;
    esac

    # 检查必要工具
    echo -e "${BLUE}[系统检查]${NC}"
    check_dependencies
    echo ""

    # 启动后端和前端
    start_backend
    local backend_ok=$?
    echo ""

    start_frontend
    local frontend_ok=$?
    echo ""

    # 显示状态
    show_status

    if [ $backend_ok -eq 0 ] || [ $frontend_ok -eq 0 ]; then
        echo ""
        echo -e "${GREEN}服务已启动！按 Ctrl+C 停止所有服务${NC}"
        echo -e "查看日志: ${BLUE}tail -f logs/backend.log${NC} 或 ${BLUE}logs/frontend.log${NC}"
        echo ""
    fi

    # 等待 Ctrl+C
    trap 'echo ""; stop_services; exit 0' INT TERM
    while true; do
        sleep 10
        [ -f "$PROJECT_DIR/.backend.pid" ] && kill -0 $(cat "$PROJECT_DIR/.backend.pid") 2>/dev/null || {
            [ -f "$PROJECT_DIR/.backend.pid" ] && error "后端进程意外退出，请检查 logs/backend.log"
        }
        [ -f "$PROJECT_DIR/.frontend.pid" ] && kill -0 $(cat "$PROJECT_DIR/.frontend.pid") 2>/dev/null || {
            [ -f "$PROJECT_DIR/.frontend.pid" ] && error "前端进程意外退出，请检查 logs/frontend.log"
        }
    done
}

main "$@"

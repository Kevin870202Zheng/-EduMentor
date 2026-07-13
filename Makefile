# ==========================================================
# 智学导师 EduMentor — Makefile
# 常用 Docker 运维命令快捷入口 (Java Spring Boot 版)
# ==========================================================

.PHONY: help dev prod build up down restart logs ps clean

# 默认目标
help:
	@echo "╔══════════════════════════════════════════════════╗"
	@echo "║     智学导师 EduMentor — 运维命令                 ║"
	@echo "╚══════════════════════════════════════════════════╝"
	@echo ""
	@echo "开发环境:"
	@echo "  make dev        启动开发环境（docker compose up -d）"
	@echo "  make dev-build  构建并启动开发环境"
	@echo "  make dev-logs   查看开发环境日志"
	@echo ""
	@echo "生产环境:"
	@echo "  make prod       启动生产环境"
	@echo "  make prod-build 构建并启动生产环境"
	@echo ""
	@echo "本地开发 (不使用 Docker):"
	@echo "  make backend    启动 Spring Boot 后端 (mvn spring-boot:run)"
	@echo "  make frontend   启动前端开发服务器 (npm run dev)"
	@echo ""
	@echo "通用命令:"
	@echo "  make up         启动所有服务"
	@echo "  make down       停止所有服务"
	@echo "  make restart     重启所有服务"
	@echo "  make ps         查看服务状态"
	@echo "  make logs       [s=backend|frontend] 查看指定服务日志"
	@echo "  make build      构建镜像"
	@echo "  make clean      清理所有容器和卷"
	@echo ""
	@echo "CI/CD:"
	@echo "  make ci         本地模拟 CI 构建检查"

dev:
	docker compose up -d

dev-build:
	docker compose up -d --build

dev-logs:
	docker compose logs -f

prod:
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

prod-build:
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# 本地后端开发 (无需 Docker)
backend:
	cd backend-java && export $$(grep -v '^#' .env | xargs) && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 本地前端开发 (无需 Docker)
frontend:
	cd frontend && npm run dev

up:
	docker compose up -d

down:
	docker compose down

restart: down up

ps:
	docker compose ps

logs:
ifdef s
	docker compose logs -f $(s)
else
	docker compose logs -f
endif

build:
	docker compose build

build-no-cache:
	docker compose build --no-cache

clean:
	docker compose down -v --remove-orphans
	docker system prune -f

# 本地 CI 模拟
ci: build
	@echo "========================================"
	@echo " CI 检查通过：镜像构建成功"
	@echo "========================================"

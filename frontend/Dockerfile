# ==========================================================
# 智学导师 EduMentor — 前端 Dockerfile
# 多阶段构建：Node 构建层 → Nginx 静态文件服务层
# ==========================================================

# ---- Stage 1: Build (构建阶段) ----
FROM node:20-alpine AS build

WORKDIR /app

# 仅复制依赖文件，利用 Docker 层缓存
COPY package.json package-lock.json* ./

# 安装依赖
RUN npm install --legacy-peer-deps --no-audit --no-fund

# 复制源代码
COPY . .

# 构建生产版本（可通过构建参数指定 API 基地址）
ARG VITE_API_BASE=/api
ENV VITE_API_BASE=${VITE_API_BASE}
ENV NODE_OPTIONS="--max-old-space-size=2048"
RUN npm run build

# ---- Stage 2: Production (Nginx 静态服务) ----
FROM nginx:1.25-alpine AS production

# 将构建产物复制到 Nginx 静态目录
COPY --from=build /app/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1

# 暴露端口
EXPOSE 3000

# 前台启动 Nginx
CMD ["nginx", "-g", "daemon off;"]

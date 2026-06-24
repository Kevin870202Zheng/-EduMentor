# =============================================================================
# EduMentor — Spring Boot 后端 Docker 多阶段构建
# =============================================================================
# Stage 1: Build — 使用 Maven + JDK 21 编译打包
# Stage 2: Runtime — 使用 JRE 21 轻量镜像运行
# =============================================================================

# ---- Stage 1: Build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

# 1. 先复制 pom.xml，利用 Docker layer caching 加速依赖下载
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# 2. 复制全部源码
COPY src src

# 3. 编译打包（跳过测试，测试在 CI 阶段执行）
RUN mvn clean package -Dmaven.test.skip=true -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# 元数据
LABEL maintainer="EduMentor Team" \
      description="EduMentor - 智学导师 Spring Boot 后端" \
      version="1.0.0"

# 系统依赖：tzdata 用于时区，ca-certificates 用于 HTTPS
RUN apk add --no-cache tzdata ca-certificates curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户
RUN addgroup -S -g 1000 edumentor && \
    adduser -S -u 1000 -G edumentor -h /app edumentor

# 创建数据目录和日志目录
RUN mkdir -p /app/data/rag /app/data/bkt /app/logs && \
    chown -R edumentor:edumentor /app

WORKDIR /app

# 从构建阶段复制 JAR 包
COPY --from=build /build/target/*.jar app.jar

# 切换非 root 用户
USER edumentor:edumentor

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+AlwaysPreTouch \
               -Djava.security.egd=file:/dev/./urandom"

# 暴露端口
EXPOSE 8080 8081

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]

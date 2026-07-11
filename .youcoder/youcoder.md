# EduMentor 智学导师 — 项目知识文档

> 首次生成: 2026-06-24 | 最后更新: 2026-07-11
> 项目路径: `/Users/roosevelt/vscode/EduMentor`

---

## 1. 项目概览

**智学导师 EduMentor** 是一个集**个性化学习路径规划、智能答疑辅导、元认知能力培养、学情数据驱动教学决策**于一体的 AI 教育智能体。

- **竞赛赛道**: 首届全国高师院校教师教学智能体应用创新设计大赛 — 赛道二：学习赋能智能体
- **核心特色**: 个性化学习路径、贝叶斯知识追踪(BKT)、RAG知识检索、苏格拉底式引导、三级预警、元认知训练

### 五大核心模块

| 模块 | 功能 | 技术亮点 |
|------|------|---------|
| ① 多维学情诊断 | 知识掌握度评估、认知画像、预警检测 | BKT知识追踪 + 三级预警模型 |
| ② 个性化学习路径 | 千人千面路径规划、前置依赖检查 | 多策略路径优化算法 |
| ③ 智能答疑辅导 | 苏格拉底式引导、RAG知识检索 | L1-L5分级答疑策略 |
| ④ 错题复盘反思 | 多维分类、归因分析、间隔复习 | 艾宾浩斯遗忘曲线 + 元认知反思 |
| ⑤ 教师智慧驾驶舱 | 班级总览、学情列表、教学建议 | 数据驱动教学决策 |

---

## 2. 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端语言 | Java | 21（LTS，必需） |
| 框架 | Spring Boot | 3.3.5 |
| ORM | Spring Data JPA + Hibernate | - |
| 数据库 | PostgreSQL | 16（含 pgvector 扩展） |
| 迁移工具 | Flyway | - |
| 认证 | Spring Security + JWT (JJWT 0.12.6) | - |
| 实时通信 | WebSocket | - |
| 前端框架 | React | 18.3 |
| 前端语言 | JavaScript (JSX) | - |
| UI库 | Ant Design | 5.20 |
| 图表 | ECharts | 5.5 |
| 后端构建 | Maven | 3.9+ |
| 前端构建 | Vite | 5.x（⚠️ 注意是 Vite 5，不是 Vite 8） |
| 容器化 | Docker + Docker Compose | - |
| CI/CD | GitHub Actions | tag 触发构建 |

---

## 3. 项目结构

```
EduMentor/
├── backend-java/                    # Java Spring Boot 后端
│   ├── src/main/java/com/edumentor/
│   │   ├── auth/                 # 认证模块 (JWT 登录/注册/刷新)
│   │   ├── config/               # 安全、CORS、WebSocket 配置
│   │   ├── course/               # 课程与知识点管理
│   │   ├── dashboard/            # 教师驾驶舱模块
│   │   ├── diagnosis/            # 学情诊断模块
│   │   ├── engine/               # 核心算法引擎
│   │   │   ├── bkt/              # BKT贝叶斯知识追踪
│   │   │   ├── ebbinghaus/       # 艾宾浩斯遗忘曲线
│   │   │   ├── llm/              # LLM多模型网关 (MOCK/OpenAI/ZHIPU/OLLAMA...)
│   │   │   └── rag/              # RAG检索增强生成
│   │   ├── enrollment/           # 选课/退课管理（含退课联动逻辑）
│   │   ├── learningpath/         # 学习路径规划
│   │   ├── notification/         # 通知服务
│   │   ├── qa/                   # 智能答疑模块
│   │   ├── record/               # 答题提交服务（含退课校验）
│   │   ├── review/               # 错题复盘模块
│   │   ├── seeder/               # 数据初始化工具 (dev profile)
│   │   ├── session/              # 学习会话实体
│   │   ├── tutoring/             # 辅导服务
│   │   ├── user/                 # 用户实体
│   │   ├── websocket/            # WebSocket实时推送
│   │   └── common/               # 公共工具与异常处理
│   ├── src/main/resources/
│   │   ├── db/migration/         # Flyway 数据库迁移 (V1~V10)
│   │   ├── application.yml       # 主配置（默认数据库: edumentor）
│   │   ├── application-dev.yml   # 开发配置（数据库: edumentor_dev）
│   │   └── application-prod.yml  # 生产配置（所有值通过环境变量注入）
│   ├── docker/
│   │   └── init.sql              # PostgreSQL 初始化（启用 pgcrypto/vector）
│   ├── Dockerfile                # 多阶段构建
│   └── pom.xml
├── frontend/                      # React 前端 (Vite 5 + JSX)
│   ├── src/
│   │   ├── pages/                # 页面组件
│   │   │   ├── StudentDashboard.jsx   # 学情总览
│   │   │   ├── StudentCourses.jsx     # 我的课程（含已退课程标签页）
│   │   │   ├── StudentLearning.jsx    # 课程学习
│   │   │   ├── LearningPath.jsx       # 学习路径
│   │   │   ├── QATutoring.jsx         # 智能答疑
│   │   │   ├── ErrorReview.jsx        # 错题复盘
│   │   │   ├── StudentProfileEdit.jsx # 个人信息
│   │   │   ├── TeacherDashboard.jsx   # 教师驾驶舱
│   │   │   ├── TeacherCourseManage.jsx# 课程管理
│   │   │   ├── Login.jsx / Register.jsx
│   │   │   └── ... 
│   │   ├── components/common/
│   │   │   └── MainLayout.jsx     # 主布局（含课程上下文管理）
│   │   ├── context/
│   │   │   └── AuthContext.jsx    # 认证上下文
│   │   └── services/
│   │       └── api.jsx            # API 统一调用层
│   ├── vite.config.js
│   └── package.json
├── docker-compose.yml              # 开发环境（后端+前端+数据库）
├── docker-compose.prod.yml         # 生产环境覆盖配置
├── start.sh                        # 本地开发一键启动脚本
├── Makefile                        # 运维快捷命令
├── .github/workflows/              # GitHub Actions CI/CD
├── docs/                           # 设计文档
│   ├── ARCHITECTURE_V2.md
│   └── 智学导师EduMentor设计方案.md
└── .youcoder/
    ├── youcoder.md                 # 本文件
    └── plans/                      # 设计分析文档
```

---

## 4. API 接口

> 基础路径: `/api`（前端通过 Vite proxy 代理到后端 8080 端口）

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 JWT |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/refresh` | 刷新 access token |
| GET | `/api/auth/me` | 获取当前用户信息 |

### 选课管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/enrollments` | 选课（支持重新选课恢复学习路径） |
| DELETE | `/api/enrollments/{id}` | 退课（联动暂停学习路径） |
| GET | `/api/enrollments/student/{studentId}` | 学生选课列表 |
| GET | `/api/enrollments/student/{studentId}/dropped` | 学生已退课程列表 |
| GET | `/api/enrollments/course/{courseId}/count` | 课程选课人数 |

### 答题
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/answers` | 提交答题（含退课校验） |

### 学情诊断
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/diagnosis/analyze` | 学情诊断分析 |
| GET | `/api/diagnosis/profile` | 认知画像 |
| GET | `/api/diagnosis/radar` | 雷达图数据 |
| GET | `/api/diagnosis/heatmap` | 学习热力图 |
| GET | `/api/diagnosis/overview` | 学情总览聚合 |

### 学习路径
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/paths/plan` | 规划学习路径 |
| GET | `/api/paths/{id}` | 路径详情 |
| GET | `/api/paths?studentId={id}` | 学生路径列表 |
| POST | `/api/paths/{id}/activate` | 激活路径 |
| POST | `/api/paths/{id}/pause` | 暂停路径 |
| POST | `/api/paths/{id}/complete` | 完成路径 |
| POST | `/api/paths/adapt` | 智能适配路径 |
| GET | `/api/paths/knowledge-graph/{courseId}` | 课程知识图谱 |

### 智能答疑
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/qa/ask` | 同步问答 |
| GET | `/api/qa/ask/stream` | SSE 流式问答 |
| GET | `/api/qa/history` | 会话历史 |
| GET | `/api/qa/sessions` | 会话列表 |

### 错题复盘
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reviews/errors` | 错题列表（按课程过滤） |
| PUT | `/api/reviews/errors/{id}/review` | 提交复盘 |
| POST | `/api/review/error-analysis` | AI 错因分析 |

### 系统
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/info` | 应用信息 |
| GET | `/swagger-ui.html` | Swagger 文档（开发环境） |

---

## 5. 数据库

### 开发环境
- **类型**: PostgreSQL 16（需本地安装或 Docker 启动）
- **数据库**: `edumentor_dev`
- **用户**: `edumentor_dev`
- **密码**: `dev@123`
- **端口**: `5432`
- **配置**: `application-dev.yml`

### Docker 环境
- **类型**: PostgreSQL 16 + pgvector (`pgvector/pgvector:0.8.0-pg16`)
- **数据库**: `edumentor`（可通过 `POSTGRES_DB` 环境变量配置）
- **用户**: `edumentor`（可通过 `POSTGRES_USER` 环境变量配置）
- **密码**: 通过 `POSTGRES_PASSWORD` 环境变量配置（必填）

### 迁移管理
- **工具**: Flyway（自动迁移，启动时执行）
- **迁移文件**: `backend-java/src/main/resources/db/migration/`
- **当前版本**: V11（详见迁移文件列表）

| 文件 | 说明 |
|------|------|
| `V1__init_schema.sql` | 初始 14 张核心表 |
| `V2__add_answer_records_updated_at.sql` | answer_records 增加 updated_at |
| `V3__add_missing_updated_at.sql` | 补充缺失的 updated_at 触发器 |
| `V4__fix_chat_history_session_id_type.sql` | session_id UUID→VARCHAR(64) |
| `V5__add_course_code_and_materials.sql` | 课程编号+资料表 |
| `V6__add_multi_course_support.sql` | 多课程支持（student_courses 等） |
| `V7__add_error_records_course_id.sql` | 错题记录加 course_id |
| `V8__add_learning_path_strategy.sql` | 学习路径策略字段 |
| `V9__add_chat_history_course_id.sql` | 对话历史加 course_id |
| `V10__add_study_sessions_course_id.sql` | 学习会话加 course_id |
| `V11__backfill_error_records_course_id.sql` | 回填 error_records 的 NULL course_id |

### 种子数据

`DataSeeder` 类仅在 `dev` 和 `default` profile 下自动运行（`@Profile({"dev", "default"})`），生产环境不执行。会创建多种角色的用户、示例课程、知识点、题目和选课数据。

**默认账号**:

| 角色 | 用户名 | 密码 |
|-----|--------|------|
| 学生 | student01~student30 | student123 |
| 教师 | teacher01 | teacher123 |
| 管理员 | admin | admin123 |

---

## 6. 快速启动

### 方式一：Docker Compose（推荐开发环境）

```bash
# 启动全部服务
cd EduMentor
docker compose up -d

# 访问地址
# - 前端: http://localhost:3000
# - 后端: http://localhost:8080
# - Swagger: http://localhost:8080/swagger-ui.html

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

### 方式二：本地开发（前后端分离）

**前置条件**:
- JDK 21（推荐 Eclipse Temurin-21）
- Node.js 18+
- PostgreSQL 16（本地运行）

**数据库准备**:
```bash
# 创建数据库和用户（如果尚未创建）
psql -U postgres -c "CREATE USER edumentor_dev WITH PASSWORD 'dev@123';"
psql -U postgres -c "CREATE DATABASE edumentor_dev OWNER edumentor_dev;"
```

**后端启动**:
```bash
cd backend-java

# 方法1: Maven 直接运行
JAVA_HOME=/path/to/jdk-21 LLM_PROVIDER=MOCK \
  mvn spring-boot:run -Dspring.profiles.active=dev -Dmaven.test.skip=true

# 方法2: 打包后运行
mvn package -Dmaven.test.skip=true -q
java -jar target/edumentor-backend-1.0.0.jar --spring.profiles.active=dev

# 关键环境变量:
# - SPRING_PROFILES_ACTIVE=dev  激活开发配置
# - LLM_PROVIDER=MOCK          使用 Mock LLM（无需 API Key）
# - JAVA_HOME                  指向 JDK 21
```

**前端启动**:
```bash
cd frontend
npm install
npm run dev
# 默认监听 http://localhost:3000
# Vite 自动代理 /api 到 http://localhost:8080
```

**一键启动**（使用 start.sh）:
```bash
./start.sh
```

---

## 7. 开发环境运维

### 7.1 常用命令

```bash
# ── 后端编译（仅检查生产代码）──
cd backend-java
mvn compile -q                          # 跳过测试编译
mvn compile                             # 完整编译（含测试）

# ── 后端运行测试（跳过已有编译错误的测试）──
mvn test -Dmaven.test.failure.ignore=true -pl . -Dtest=\!KnowledgeServiceTest

# ── 后端重新编译（清理后全量）──
mvn clean compile -Dmaven.test.skip=true

# ── 后端 JAR 打包（跳过测试）──
mvn package -Dmaven.test.skip=true -q

# ── 前端构建 ──
cd frontend
npm run build          # 生产构建，输出到 dist/
npm run dev            # 开发服务器（热更新）
npx vite --port 3000   # 指定端口启动

# ── 数据库操作 ──
psql -U edumentor_dev -d edumentor_dev   # 连接开发数据库
psql -U edumentor_dev -d edumentor_dev -c "SELECT version,description FROM flyway_schema_history ORDER BY version;"  # 查看迁移状态

# ── Flyway 手动操作（如需）──
mvn flyway:migrate -Dspring.profiles.active=dev   # 手动执行迁移
mvn flyway:info -Dspring.profiles.active=dev      # 查看迁移信息
```

### 7.2 后端端口占用处理

```bash
# 查看端口占用
lsof -i :8080

# 释放端口
lsof -ti:8080 | xargs kill -9

# 或指定其他端口启动
java -jar target/edumentor-backend-1.0.0.jar --server.port=8081
```

### 7.3 Java 版本管理

项目要求 **JDK 21**。如果默认 Java 版本不是 21：

```bash
# 查看可用 JDK
/usr/libexec/java_home -V

# 使用特定 JDK 运行
JAVA_HOME=/path/to/jdk-21 mvn spring-boot:run ...

# macOS Homebrew 管理
brew install temurin21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

⚠️ **已知问题**: 项目使用 Spring Boot 3.3.5，不兼容 Java 25+。如果用 Java 25 编译，class 文件版本过高（69.0），Java 21 运行时无法加载。需用 Java 21 **编译 + 运行**。

### 7.4 LLM 提供商配置

后端通过 `LLM_PROVIDER` 环境变量选择 LLM 提供商：

| 提供商 | 环境变量 | 说明 |
|--------|---------|------|
| `MOCK` | `LLM_PROVIDER=MOCK` | 模拟响应，无需 API Key（开发推荐） |
| `OPENAI` | `LLM_PROVIDER=OPENAI` + `OPENAI_API_KEY` | OpenAI 兼容 API |
| `DEEPSEEK` | `LLM_PROVIDER=DEEPSEEK` + `DEEPSEEK_API_KEY` | DeepSeek 模型 |
| `ZHIPU` | `LLM_PROVIDER=ZHIPU` + `ZHIPUAI_API_KEY` | 智谱 AI |
| `OLLAMA` | `LLM_PROVIDER=OLLAMA` + `OLLAMA_BASE_URL` | 本地 Ollama |

---

## 8. 服务器环境运维

### 8.1 生产服务器

```
主机:     (以实际为准)
用户:     (以实际为准)
项目路径: (以实际为准)
```

### 8.2 Docker 部署

```bash
# ── 基础操作 ──
cd /path/to/EduMentor

# 启动（开发模式）
docker compose up -d

# 启动（生产模式）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 重新构建并启动
docker compose up -d --build

# 停止
docker compose down

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f           # 所有服务
docker compose logs -f backend   # 仅后端
docker compose logs -f frontend  # 仅前端
docker compose logs -f db        # 仅数据库

# ── 容器管理 ──
docker exec -it edumentor-app bash        # 进入后端容器
docker exec -it edumentor-postgres psql -U edumentor -d edumentor   # 连接数据库
docker restart edumentor-app              # 重启后端
docker logs edumentor-app --tail 100      # 查看最后 100 行日志

# ── 数据卷管理 ──
docker volume ls | grep edumentor         # 列出所有数据卷
docker volume inspect edumentor_postgres_data  # 查看数据卷详情

# ── 清理 ──
docker compose down -v                    # 停止并删除数据卷（⚠️ 数据会丢失）
docker system prune -a                    # 清理所有未使用的 Docker 资源
```

### 8.3 GitHub Actions CI/CD

CI/CD 配置在 `.github/workflows/` 目录下。

- **触发条件**: 仅打 tag 时构建（非每次 push）
- **构建内容**: 后端 JAR + Docker 镜像 + 前端静态文件
- **部署方式**: SSH 到服务器后 docker compose up

### 8.4 Nginx 反向代理（生产环境建议）

生产环境建议在 Docker 前加 Nginx 反向代理：

```nginx
# /etc/nginx/sites-available/edumentor
server {
    listen 80;
    server_name edumentor.example.com;

    # 前端静态文件（也可直接代理到 :3000）
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### 8.5 数据库备份与恢复

```bash
# ── 备份 ──
docker exec -t edumentor-postgres pg_dump -U edumentor edumentor > backup_$(date +%Y%m%d).sql

# ── 恢复 ──
cat backup.sql | docker exec -i edumentor-postgres psql -U edumentor edumentor
```

### 8.6 生产环境关键环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `POSTGRES_PASSWORD` | 数据库密码 | ✅ |
| `JWT_SECRET_KEY` | JWT 签名密钥（至少 256 位） | ✅ |
| `SPRING_PROFILES_ACTIVE` | Spring Profile（生产用 prod） | ✅ |
| `CORS_ALLOWED_ORIGINS` | 跨域来源 | 推荐 |
| `LLM_PROVIDER` | LLM 提供商 | 推荐 |
| `OPENAI_API_KEY` | OpenAI/DeepSeek API Key | 按需 |

---

## 9. 核心功能模块说明

### 9.1 选课与退课联动

退课（`EnrollmentService.dropCourse`）不仅仅标记选课状态为 `dropped`，还会触发以下联动：

| 联动项 | 行为 | 实现位置 |
|--------|------|---------|
| 学习路径 | 退课 → 暂停 ACTIVE/DRAFT 路径为 PAUSED | `EnrollmentService.dropCourse()` |
| 学习路径 | 重新选课 → 恢复 PAUSED 路径为 DRAFT | `EnrollmentService.enroll()` |
| 答题提交 | 退课后禁止提交该课程的答题 | `AnswerService.submitAnswer()` |
| 前端上下文 | 退课后自动切换/清理 selectedCourseId | `MainLayout.jsx` + `StudentCourses.jsx` |
| 历史记录 | 保留已退课程查询接口，支持重新选课 | `GET /api/enrollments/student/{id}/dropped` |

### 9.2 答题流程

```
提交答题 → 校验选课状态 → 比对答案 → 保存记录
  ├── 答错 → 创建错题记录 (error_records)
  ├── 答对 → 推进学习路径节点 (learning_path_nodes)
  └── 每次 → 更新学习会话 (study_sessions, 含 course_id)
```

---

## 10. 已知问题与注意事项

### 认证系统
- 前后端 Token Key: `edumentor_access_token`（统一使用此 key）
- 后端返回字段: `accessToken` / `refreshToken`（camelCase）
- Token 刷新在 `api.jsx` 的响应拦截器中自动处理

### 前端构建
- **Vite 5**（非 Vite 8）：降级到 Vite 5 是因为 Vite 8 (Rolldown) 对 React CJS 模块的 ESM 转换存在 bug
- 使用 `manualChunks` 将 React/Ant Design/echarts/axios 单独分包
- Docker 构建时设置 `NODE_OPTIONS=--max-old-space-size=2048` 防止 OOM

### 后端配置
- `ddl-auto: none` — Flyway 管理 Schema，Hibernate 不做 DDL 操作
- **错题记录 course_id 修复 (V11)**: 部分通过 ReviewController API 创建的 error_records 缺少 course_id，导致退课后无法按课程查询。V11 通过 `questions.course_id` 回填 NULL 值，同时 `ReviewService.recordError()` 新增 courseId 参数防止再次出现
- CORS 配置统一在 `SecurityConfig.java` 中
- JWT 异常日志级别: `warn`
- DataSeeder 仅在 `dev`/`default` profile 下运行，生产环境不执行

### 测试注意事项
- 测试代码 `KnowledgeServiceTest.java` 存在预存的编译错误（未及时更新），不影响生产代码
- 运行 `mvn spring-boot:run` 时需添加 `-Dmaven.test.skip=true` 跳过测试编译
- 运行测试时使用 `mvn test -Dtest=\!KnowledgeServiceTest` 排除该测试类

### 其他
- 本地开发前端使用 `http://localhost:3000`，通过 Vite proxy 代理 `/api` 到后端
- Docker Compose 中前端通过 nginx 容器服务后端，代理路径一致
- 学习路径表 `learning_paths` 的 `status` 枚举值: `DRAFT`, `ACTIVE`, `COMPLETED`, `PAUSED`
- 选课表 `student_courses` 的 `status` 枚举值: `active`, `completed`, `dropped`

---

## 11. 设计文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 产品设计方案 | `docs/智学导师EduMentor设计方案.md` | 完整产品设计 |
| V2 架构设计 | `docs/ARCHITECTURE_V2.md` | 架构重构方案 |
| 退课联动分析 | `.youcoder/plans/drop-course-linkage-analysis.html` | 退课联动设计 |
| 代码审查报告 | `.youcoder/code-review.html` | 代码审查结果 |

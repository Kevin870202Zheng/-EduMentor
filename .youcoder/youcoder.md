# EduMentor 智学导师 — 项目知识文档

> 首次生成: 2026-06-24 | 最后更新: 2026-06-24
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
| 后端语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.5 |
| ORM | Spring Data JPA | - |
| 数据库 | PostgreSQL | 16 |
| 迁移工具 | Flyway | - |
| 认证 | Spring Security + JWT (JJWT 0.12.6) | - |
| 实时通信 | WebSocket | - |
| 前端框架 | React | 18.3 |
| 前端语言 | TypeScript | 5.5 |
| UI库 | Ant Design | 5.20 |
| 图表 | ECharts | 5.5 |
| 构建工具 | Maven / Vite 5 | - |
| 容器化 | Docker + Docker Compose | - |

---

## 3. 项目结构

```
EduMentor/
├── backend-java/                # Java Spring Boot 后端
│   ├── src/main/java/com/edumentor/
│   │   ├── auth/            # 认证模块 (JWT)
│   │   ├── config/          # 安全、CORS、WebSocket 配置
│   │   ├── course/          # 课程与知识点管理
│   │   ├── diagnosis/       # 学情诊断模块
│   │   ├── dashboard/       # 教师驾驶舱模块
│   │   ├── engine/          # 核心算法引擎
│   │   │   ├── bkt/         # BKT贝叶斯知识追踪
│   │   │   ├── ebbinghaus/  # 艾宾浩斯遗忘曲线
│   │   │   ├── llm/         # LLM多模型网关
│   │   │   └── rag/         # RAG检索增强生成
│   │   ├── knowledge/       # RAG知识管理
│   │   ├── learningpath/    # 学习路径规划
│   │   ├── notification/    # 通知服务
│   │   ├── qa/              # 智能答疑模块
│   │   ├── review/          # 错题复盘模块
│   │   ├── seeder/          # 数据初始化工具
│   │   ├── tutoring/        # 辅导服务
│   │   ├── websocket/       # WebSocket实时推送
│   │   └── common/          # 公共工具与异常处理
│   ├── src/main/resources/
│   │   ├── db/migration/    # Flyway 数据库迁移
│   │   ├── application.yml  # 主配置
│   │   ├── application-dev.yml
│   │   └── application-prod.yml
│   └── pom.xml
├── frontend/                    # React 前端 (Vite 5 + TypeScript)
│   ├── src/
│   │   ├── api/                 # TypeScript API 层 (apiClient.ts)
│   │   ├── pages/               # 页面组件
│   │   ├── services/            # API 服务层 (api.jsx — 当前主要使用)
│   │   ├── components/          # 通用组件
│   │   └── context/             # React Context (AuthContext)
│   └── package.json
├── docs/                        # 设计文档
│   ├── ARCHITECTURE_V2.md       # V2 架构设计方案
│   └── 智学导师EduMentor设计方案.md
├── docker-compose.yml           # 开发环境配置
├── docker-compose.prod.yml      # 生产环境覆盖配置
├── Makefile                     # 运维快捷命令
├── start.sh                     # 启动脚本
└── README.md
```

---

## 4. API 接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 登录 | POST | `/api/v1/auth/login` | 用户登录，返回 JWT |
| 注册 | POST | `/api/v1/auth/register` | 用户注册 |
| 刷新 Token | POST | `/api/v1/auth/refresh` | 刷新 access token |
| 学情分析 | POST | `/api/v1/diagnosis/analyze` | 提交答题数据，返回认知画像 |
| 雷达图 | GET | `/api/v1/diagnosis/radar/{id}` | 四维学情雷达 |
| 学习路径 | GET | `/api/v1/path/plan` | 个性化学习路径 |
| 知识图谱 | GET | `/api/v1/path/knowledge-graph/{id}` | 课程知识图谱 |
| 智能答疑 | POST | `/api/v1/qa/ask` | 分级引导式答疑 |
| 错题分析 | POST | `/api/v1/review/error-analysis` | 提交错题分析 |
| 教师总览 | GET | `/api/v1/dashboard/summary` | 班级学情总览 |
| 通知列表 | GET | `/api/v1/notifications` | 获取用户通知 |
| RAG知识检索 | POST | `/api/v1/knowledge/search` | 语义检索知识库 |
| 辅导会话 | POST | `/api/v1/tutoring/session` | 创建/继续辅导会话 |
| 健康检查 | GET | `/actuator/health` | Spring Boot Actuator |

---

## 5. 快速启动

### Docker Compose（推荐）

```bash
cd EduMentor
docker compose up -d
```

服务访问地址:
- 后端 API: `http://localhost:8080`
- 前端应用: `http://localhost:3000`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### 本地开发

**后端** (JDK 21+):
```bash
cd backend-java
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端** (Node.js 18+):
```bash
cd frontend
npm install
npm run dev
```

---

## 6. 数据库

- **类型**: PostgreSQL 16 (Docker: `postgres:16-alpine`)
- **默认库名**: `edumentor`
- **默认用户**: `edumentor`
- **默认密码**: `edumentor@123`
- **端口**: `5432`
- **健康检查**: `pg_isready`
- **迁移工具**: Flyway (`classpath:db/migration/`)
- **初始化 SQL**: `backend-java/docker/init.sql`

### Flyway 迁移文件

| 文件 | 说明 |
|------|------|
| `V1__init_schema.sql` | 初始建表 |
| `V2__add_updated_at.sql` | add updated_at to answer_records |
| `V3__add_updated_at_chat_knowledge.sql` | add updated_at to chat_history/knowledge_relations |
| `V4__fix_session_id_type.sql` | chat_history.session_id UUID→VARCHAR(64) |

### 种子数据

`SeederController` / `DataSeeder` 类仅在 `dev` 和 `default` profile 下运行（`@Profile({"dev", "default"})`），生产环境不执行。

**默认账号**:

| 角色 | 用户名 | 密码 |
|-----|--------|------|
| 学生 | student1 | 123456 |
| 教师 | teacher1 | 123456 |
| 管理员 | admin | admin123 |

---

## 7. 部署与运维

### 生产服务器

- **主机**: `82.156.203.220`
- **用户**: `ubuntu`
- **项目路径**: `/home/ubuntu/EduMentor`
- **连接**: `ssh ubuntu@82.156.203.220` (密码: `H,T4JP7$v+a-`)

### 部署命令

```bash
# 上传项目
scp -r EduMentor ubuntu@82.156.203.220:/home/ubuntu/

# SSH 登录
ssh ubuntu@82.156.203.220

# 启动
cd /home/ubuntu/EduMentor
docker compose up -d

# 或者生产模式
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 访问地址

| 地址 | 说明 |
|------|------|
| `http://82.156.203.220:3000` | 前端页面 |
| `http://82.156.203.220:8080` | 后端 API |
| `http://82.156.203.220:8080/actuator/health` | 健康检查 |

### Makefile 命令

```bash
make dev          # 启动开发环境
make prod         # 启动生产环境
make down         # 停止所有服务
make ps           # 查看服务状态
make logs s=backend   # 查看日志
make build        # 构建镜像
make clean        # 清理所有容器和卷
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POSTGRES_PASSWORD` | (必填) | 数据库密码 |
| `JWT_SECRET_KEY` | 开发密钥 | JWT 签名密钥（生产需更换） |
| `JWT_ACCESS_TOKEN_EXPIRATION` | 3600 | Access Token 过期时间（秒） |
| `JWT_REFRESH_TOKEN_EXPIRATION` | 2592000 | Refresh Token 过期时间（秒） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://82.156.203.220:3000` | 允许的跨域来源 |
| `SPRING_PROFILES_ACTIVE` | dev | Spring Profile |
| `LLM_PROVIDER` | mock | LLM 供应商 |
| `RAG_ENABLED` | true | RAG 开关 |

---

## 8. Docker Compose 服务

| 服务 | 容器名 | 内部端口 | 外部端口 | 依赖 |
|------|--------|---------|---------|------|
| `backend` | edumentor-app | 8080/8081 | 8080/8081 | db (health) |
| `frontend` | edumentor-frontend | 3000 | 3000 | backend |
| `db` | edumentor-postgres | 5432 | 5432 | - |

数据卷:
- `edumentor_postgres_data` — PostgreSQL 数据
- `edumentor_rag_data` — RAG 向量存储
- `edumentor_bkt_data` — BKT 模型数据
- `edumentor_app_logs` — 应用日志
- `edumentor_rag` / `edumentor_bkt` / `edumentor_logs` — 生产环境卷名

---

## 9. 已知问题与注意事项

### 认证系统
- 前后端 Token Key: `edumentor_access_token`（统一使用此 key）
- 后端返回字段: `accessToken` / `refreshToken`（camelCase）
- 前端主要使用 `services/api.jsx`（旧系统），`api/apiClient.ts`（TypeScript 新系统）目前未被使用
- Token 刷新在 `api.jsx`的响应拦截器中自动处理

### 前端构建
- **Vite 5**（非 Vite 8）：降级到 Vite 5 是因为 Vite 8 (Rolldown) 对 React CJS 模块的 ESM 转换存在 bug
- 使用 `manualChunks` 分包（React/Ant Design/echarts/axios 单独打包）
- Docker 构建时设置 `NODE_OPTIONS=--max-old-space-size=2048` 防止 OOM

### 后端配置
- `ddl-auto: none` — Flyway 管理 Schema，Hibernate 不做 DDL 操作
- CORS 配置统一在 `SecurityConfig.java` 中（`CorsConfig.java` 已被删除）
- JWT 异常日志级别: `warn`
- JwtAuthFilter 使用 `ConcurrentHashMap` 缓存 User 实体（5分钟TTL），避免每次查库

### 代码审查历史
- 所有已修复问题记录在 `.youcoder/code-review.html`
- 修复范围: Token刷新字段名、CORS配置重复、JWT查库性能、Token key 统一、refresh参数名、硬编码课程ID、nginx注释、Token过期时间统一、日志级别、防御性null检查、Vite版本降级

---

## 10. 设计文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 产品设计方案 | `docs/智学导师EduMentor设计方案.md` | 完整产品设计 |
| V2 架构设计 | `docs/ARCHITECTURE_V2.md` | 架构重构方案 |
| 竞赛通知 | `docs/首届全国高师院校教师教学智能体应用创新设计大赛通知.md` | 竞赛要求 |
| 代码审查报告 | `.youcoder/code-review.html` | 代码审查结果 |

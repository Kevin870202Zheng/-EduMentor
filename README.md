# 智学导师 EduMentor — 学习赋能AI教育智能体

> **竞赛赛道**：首届全国高师院校教师教学智能体应用创新设计大赛 — 赛道二：学习赋能智能体  
> **技术栈**：Java Spring Boot 3 + Spring Data JPA + PostgreSQL + React + Ant Design + ECharts  
> **核心特色**：个性化学习路径、贝叶斯知识追踪(BKT)、RAG知识检索、苏格拉底式引导、三级预警、元认知训练

---

## 📋 项目概述

**智学导师 EduMentor** 是一个集**个性化学习路径规划、智能答疑辅导、元认知能力培养、学情数据驱动教学决策**于一体的 AI 教育智能体。

### 五大核心模块

| 模块 | 功能 | 技术亮点 |
|------|------|---------|
| ① 多维学情诊断 | 知识掌握度评估、认知画像、预警检测 | BKT知识追踪 + 三级预警模型 |
| ② 个性化学习路径 | 千人千面路径规划、前置依赖检查 | 多策略路径优化算法 |
| ③ 智能答疑辅导 | 苏格拉底式引导、RAG知识检索 | L1-L5分级答疑策略 |
| ④ 错题复盘反思 | 多维分类、归因分析、间隔复习 | 艾宾浩斯遗忘曲线 + 元认知反思 |
| ⑤ 教师智慧驾驶舱 | 班级总览、学情列表、教学建议 | 数据驱动教学决策 |

---

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

```bash
cd EduMentor
docker compose up -d
```

- 后端 API: `http://localhost:8080`
- 前端应用: `http://localhost:3000`
- Swagger 文档: `http://localhost:8080/swagger-ui.html`

### 方式二：本地开发

**后端启动（需要 JDK 21+）：**

```bash
cd EduMentor/backend-java
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端启动（需要 Node.js 18+）：**

```bash
cd EduMentor/frontend
npm install
npm run dev
```

---

## 📚 项目结构

```
EduMentor/
├── backend-java/                # Java Spring Boot 后端
│   ├── src/
│   │   ├── main/java/com/edumentor/
│   │   │   ├── auth/            # 认证模块 (JWT)
│   │   │   ├── config/          # 安全、CORS、WebSocket 配置
│   │   │   ├── course/          # 课程与知识点管理
│   │   │   ├── diagnosis/       # 学情诊断模块
│   │   │   ├── dashboard/       # 教师驾驶舱模块
│   │   │   ├── engine/          # 核心算法引擎
│   │   │   │   ├── bkt/         # BKT贝叶斯知识追踪
│   │   │   │   ├── ebbinghaus/  # 艾宾浩斯遗忘曲线
│   │   │   │   ├── llm/         # LLM多模型网关
│   │   │   │   └── rag/         # RAG检索增强生成
│   │   │   ├── knowledge/       # RAG知识管理
│   │   │   ├── learningpath/    # 学习路径规划
│   │   │   ├── notification/    # 通知服务
│   │   │   ├── qa/              # 智能答疑模块
│   │   │   ├── review/          # 错题复盘模块
│   │   │   ├── seeder/          # 数据初始化工具
│   │   │   ├── tutoring/        # 辅导服务
│   │   │   ├── websocket/       # WebSocket实时推送
│   │   │   └── common/          # 公共工具与异常处理
│   │   ├── main/resources/
│   │   │   ├── db/migration/    # Flyway 数据库迁移
│   │   │   ├── application.yml  # 主配置
│   │   │   ├── application-dev.yml
│   │   │   └── application-prod.yml
│   │   └── test/                # 单元测试
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                    # React 前端 (TypeScript)
│   ├── src/
│   │   ├── api/                 # TypeScript API 层
│   │   ├── pages/               # 页面组件
│   │   ├── services/            # API 服务层 (JS)
│   │   ├── components/          # 通用组件
│   │   └── context/             # React Context
│   ├── vite.config.js
│   ├── tsconfig.json
│   └── package.json
├── docker-compose.yml           # 开发环境配置
├── docker-compose.prod.yml      # 生产环境覆盖配置
├── Makefile                     # 运维快捷命令
└── README.md
```

## 🔗 API 接口一览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 登录 | POST | `/api/v1/auth/login` | 用户登录，返回JWT令牌 |
| 注册 | POST | `/api/v1/auth/register` | 用户注册 |
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

## 🏆 竞赛评审对应策略

| 评审维度 | 分值 | EduMentor 对应策略 |
|---------|------|-------------------|
| 合规与伦理 | 15分 | 数据脱敏、透明可解释、人为干预机制、伦理保障体系 |
| 创新价值 | 20分 | 苏格拉底式引导、元认知训练、三级预警、多策略路径 |
| 技术实现 | 25分 | Spring Boot架构、BKT算法、RAG检索、模块化设计 |
| 教学融合 | 25分 | 课前-课中-课后全流程、人机协同、掌握学习法 |
| 应用成效 | 15分 | 个性化路径、错题间隔复习、教师驾驶舱决策支持 |

## 🛠️ 技术栈

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
| 构建工具 | Maven / Vite | - |
| 容器化 | Docker + Docker Compose | - |

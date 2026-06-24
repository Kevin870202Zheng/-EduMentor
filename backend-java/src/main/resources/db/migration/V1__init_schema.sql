-- ============================================================================
-- EduMentor (智学导师) — PostgreSQL 数据库初始化 Schema
-- Flyway Migration: V1__init_schema.sql
--
-- 包含 14 张核心业务表，支持以下功能模块：
--   用户认证 (JWT)     · 学情诊断     · 学习路径规划    · 智能答疑 (LLM+RAG)
--   错题复盘 (艾宾浩斯) · 预警系统     · 教师驾驶舱      · 知识管理
--   WebSocket 实时通信
--
-- 约定：
--   - 所有表均使用 UUID 主键 (通过 JPA GenerationType.UUID 配合 gen_random_uuid())
--   - 所有表均含 created_at / updated_at 审计字段 (继承 BaseEntity)
--   - JSONB 用于存储灵活的半结构化数据
--   - 时间字段统一使用 timestamptz (TIMESTAMP WITH TIME ZONE)
--   - CHECK 约束与 JPA @Enumerated(EnumType.STRING) 枚举值严格对齐
-- ============================================================================

-- 确保 UUID 扩展已安装
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. users — 用户账号表
--    支持 STUDENT / TEACHER / ADMIN 三种角色
-- ============================================================================
CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64) NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(64),
    avatar_url      VARCHAR(512),
    role            VARCHAR(16) NOT NULL DEFAULT 'STUDENT'
                    CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    phone           VARCHAR(20),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_users_username ON users (username);
CREATE UNIQUE INDEX idx_users_email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_active_role ON users (is_active, role) WHERE is_active = TRUE;

-- ============================================================================
-- 2. student_profiles — 学生扩展信息表
--    与 users 为一对一关系；bkt_state 存储 BKT 引擎的认知状态快照
-- ============================================================================
CREATE TABLE student_profiles (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL UNIQUE,
    grade                   VARCHAR(32),
    school                  VARCHAR(64),
    target_school           VARCHAR(64),
    exam_date               DATE,
    learning_style          VARCHAR(32),
    weak_areas              JSONB       DEFAULT '[]'::jsonb,
    strengths               JSONB       DEFAULT '[]'::jsonb,
    daily_study_minutes     INTEGER,
    learning_efficiency     NUMERIC(5,2),
    bkt_state               JSONB,
    metadata                JSONB       DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_student_profiles_grade ON student_profiles (grade);
CREATE INDEX idx_student_profiles_user ON student_profiles (user_id);

-- ============================================================================
-- 3. courses — 课程表
-- ============================================================================
CREATE TABLE courses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    subject         VARCHAR(64),
    grade_level     VARCHAR(32),
    cover_url       VARCHAR(512),
    is_published    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by      UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_courses_created_by FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_courses_subject ON courses (subject);
CREATE INDEX idx_courses_created_by ON courses (created_by);
CREATE INDEX idx_courses_published ON courses (is_published) WHERE is_published = TRUE;

-- ============================================================================
-- 4. knowledge_points — 知识点表
--    支持课程下的树状/网状知识结构
-- ============================================================================
CREATE TABLE knowledge_points (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id           UUID        NOT NULL,
    parent_kp_id        UUID,
    name                VARCHAR(256) NOT NULL,
    description         TEXT,
    content             TEXT,
    difficulty          SMALLINT    NOT NULL DEFAULT 3
                        CHECK (difficulty BETWEEN 1 AND 5),
    importance          SMALLINT    NOT NULL DEFAULT 3
                        CHECK (importance BETWEEN 1 AND 5),
    subject             VARCHAR(64),
    tags                JSONB       DEFAULT '[]'::jsonb,
    order_index         INTEGER     NOT NULL DEFAULT 0,
    estimated_minutes   INTEGER     DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_kp_course FOREIGN KEY (course_id)
        REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_kp_parent FOREIGN KEY (parent_kp_id)
        REFERENCES knowledge_points(id) ON DELETE SET NULL
);

CREATE INDEX idx_kp_course_id ON knowledge_points (course_id);
CREATE INDEX idx_kp_parent_kp_id ON knowledge_points (parent_kp_id);
CREATE INDEX idx_kp_course_order ON knowledge_points (course_id, order_index);
CREATE INDEX idx_kp_subject ON knowledge_points (subject);
CREATE INDEX idx_kp_tags ON knowledge_points USING gin (tags);

-- ============================================================================
-- 5. knowledge_relations — 知识点关系表
--    支持 PREREQUISITE（前置依赖）/ PARENT_OF（父子）/ RELATED（相关） 关系
-- ============================================================================
CREATE TABLE knowledge_relations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_kp_id    UUID        NOT NULL,
    target_kp_id    UUID        NOT NULL,
    relation_type   VARCHAR(32) NOT NULL
                    CHECK (relation_type IN ('PREREQUISITE', 'PARENT_OF', 'RELATED')),
    weight          NUMERIC(5,2) DEFAULT 1.00
                    CHECK (weight >= 0 AND weight <= 1),
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_kr_source FOREIGN KEY (source_kp_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    CONSTRAINT fk_kr_target FOREIGN KEY (target_kp_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    CONSTRAINT uk_kp_relation UNIQUE (source_kp_id, target_kp_id, relation_type)
);

CREATE INDEX idx_kr_source ON knowledge_relations (source_kp_id);
CREATE INDEX idx_kr_target ON knowledge_relations (target_kp_id);
CREATE INDEX idx_kr_type ON knowledge_relations (relation_type);

-- ============================================================================
-- 6. learning_paths — 学习路径表
--    记录系统推荐或教师制定的个性化学习计划
-- ============================================================================
CREATE TABLE learning_paths (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID        NOT NULL,
    course_id           UUID,
    created_by          UUID,
    name                VARCHAR(255),
    description         TEXT,
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'PAUSED')),
    progress            INTEGER     NOT NULL DEFAULT 0
                        CHECK (progress >= 0 AND progress <= 100),
    total_nodes         INTEGER     DEFAULT 0,
    completed_nodes     INTEGER     DEFAULT 0,
    daily_minutes       INTEGER,
    metadata            JSONB       DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_lp_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lp_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_lp_student ON learning_paths (student_id);
CREATE INDEX idx_lp_status ON learning_paths (status);
CREATE INDEX idx_lp_student_status ON learning_paths (student_id, status);

-- ============================================================================
-- 7. learning_path_nodes — 学习路径节点表
--    路径中的每一步，关联具体知识点
-- ============================================================================
CREATE TABLE learning_path_nodes (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_path_id        UUID        NOT NULL,
    knowledge_point_id      UUID        NOT NULL,
    knowledge_point_name    VARCHAR(255),
    order_index             INTEGER     NOT NULL DEFAULT 0,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    is_recommended          BOOLEAN     NOT NULL DEFAULT TRUE,
    completed_at            TIMESTAMPTZ,
    estimated_minutes       INTEGER     DEFAULT 0,
    actual_minutes          INTEGER     DEFAULT 0,
    mastery_threshold       DOUBLE PRECISION,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_lpn_path FOREIGN KEY (learning_path_id)
        REFERENCES learning_paths(id) ON DELETE CASCADE,
    CONSTRAINT fk_lpn_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE
);

CREATE INDEX idx_lpn_path ON learning_path_nodes (learning_path_id);
CREATE INDEX idx_lpn_path_order ON learning_path_nodes (learning_path_id, order_index);
CREATE INDEX idx_lpn_kp ON learning_path_nodes (knowledge_point_id);
CREATE INDEX idx_lpn_status ON learning_path_nodes (status);

-- ============================================================================
-- 8. questions — 题目表
--    支持多种题型：单/多选、判断、填空、简答、编程
-- ============================================================================
CREATE TABLE questions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_point_id  UUID        NOT NULL,
    course_id           UUID        NOT NULL,
    question_type       VARCHAR(32) NOT NULL
                        CHECK (question_type IN (
                            'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE',
                            'FILL_BLANK', 'SHORT_ANSWER', 'CODING'
                        )),
    difficulty          SMALLINT    NOT NULL DEFAULT 3
                        CHECK (difficulty BETWEEN 1 AND 5),
    content             TEXT        NOT NULL,
    options             JSONB       DEFAULT '[]'::jsonb,
    correct_answer      TEXT        NOT NULL,
    explanation         TEXT,
    tags                JSONB       DEFAULT '[]'::jsonb,
    is_published        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_q_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    CONSTRAINT fk_q_course FOREIGN KEY (course_id)
        REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_q_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_q_kp_id ON questions (knowledge_point_id);
CREATE INDEX idx_q_course_id ON questions (course_id);
CREATE INDEX idx_q_question_type ON questions (question_type);
CREATE INDEX idx_q_difficulty ON questions (difficulty);
CREATE INDEX idx_q_published ON questions (is_published) WHERE is_published = TRUE;
CREATE INDEX idx_q_tags ON questions USING gin (tags);

-- ============================================================================
-- 9. answer_records — 作答记录表
--    记录学生每次答题的详细数据，用于学情诊断与 BKT 计算
-- ============================================================================
CREATE TABLE answer_records (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID        NOT NULL,
    question_id         UUID        NOT NULL,
    knowledge_point_id  UUID        NOT NULL,
    course_id           UUID,
    is_correct          BOOLEAN     NOT NULL,
    student_answer      TEXT,
    time_spent_seconds  INTEGER     DEFAULT 0,
    attempted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata            JSONB       DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ar_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_question FOREIGN KEY (question_id)
        REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE
);

CREATE INDEX idx_ar_student_kp ON answer_records (student_id, knowledge_point_id);
CREATE INDEX idx_ar_student_correct ON answer_records (student_id, is_correct);
CREATE INDEX idx_ar_attempted_at ON answer_records (student_id, attempted_at DESC);

-- ============================================================================
-- 10. error_records — 错题记录表
--     自动从答案记录中生成，附带 AI 错因分析
-- ============================================================================
CREATE TABLE error_records (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID        NOT NULL,
    question_id             UUID        NOT NULL,
    knowledge_point_id      UUID        NOT NULL,
    knowledge_point_name    VARCHAR(255),
    question_content        TEXT,
    student_answer          TEXT,
    correct_answer          TEXT,
    error_type              VARCHAR(32)
                            CHECK (error_type IN (
                                'KNOWLEDGE_GAP', 'CARELESS', 'MISUNDERSTANDING',
                                'METHOD_ERROR', 'TIME_OUT', 'OTHER'
                            )),
    error_analysis          TEXT,
    review_suggestion       TEXT,
    difficulty              INTEGER     DEFAULT 3,
    is_reviewed             BOOLEAN     NOT NULL DEFAULT FALSE,
    review_accuracy         NUMERIC(5,2),
    error_count             INTEGER     DEFAULT 1,
    metadata                JSONB       DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_er_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_question FOREIGN KEY (question_id)
        REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE
);

CREATE INDEX idx_er_student_kp ON error_records (student_id, knowledge_point_id);
CREATE INDEX idx_er_student_reviewed ON error_records (student_id, is_reviewed);
CREATE INDEX idx_er_kp_id ON error_records (knowledge_point_id);
CREATE INDEX idx_er_question_id ON error_records (question_id);
CREATE INDEX idx_er_error_type ON error_records (error_type);
CREATE INDEX idx_er_student_created ON error_records (student_id, created_at);

-- ============================================================================
-- 11. review_records — 复习记录表
--     基于艾宾浩斯遗忘曲线排程，记录每次复习及掌握度变化
-- ============================================================================
CREATE TABLE review_records (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID        NOT NULL,
    knowledge_point_id      UUID        NOT NULL,
    knowledge_point_name    VARCHAR(255),
    error_record_id         UUID,
    review_type             VARCHAR(32)
                            CHECK (review_type IN (
                                'SCHEDULED_REVIEW', 'ERROR_REVIEW',
                                'CUSTOM_REVIEW', 'EXAM_REVIEW'
                            )),
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED', 'OVERDUE')),
    review_cycle            INTEGER,
    scheduled_date          DATE        NOT NULL,
    completed_date          DATE,
    spent_minutes           INTEGER,
    effectiveness_score     INTEGER,
    accuracy                NUMERIC(5,2),
    notes                   TEXT,
    next_review_date        DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_rr_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_error FOREIGN KEY (error_record_id)
        REFERENCES error_records(id) ON DELETE SET NULL
);

CREATE INDEX idx_rr_student ON review_records (student_id);
CREATE INDEX idx_rr_scheduled ON review_records (scheduled_date);
CREATE INDEX idx_rr_next_review ON review_records (student_id, next_review_date);
CREATE INDEX idx_rr_status ON review_records (student_id, status);
CREATE INDEX idx_rr_kp_id ON review_records (knowledge_point_id);
CREATE INDEX idx_rr_student_cycle ON review_records (student_id, review_cycle);

-- ============================================================================
-- 12. alert_records — 预警记录表
--     支持多级别预警（LOW / MEDIUM / HIGH / CRITICAL），自动触发或教师手动创建
-- ============================================================================
CREATE TABLE alert_records (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID        NOT NULL,
    teacher_id      UUID,
    alert_type      VARCHAR(32) NOT NULL
                    CHECK (alert_type IN (
                        'PERFORMANCE_DECLINE', 'KNOWLEDGE_GAP', 'STUDY_ENGAGEMENT',
                        'ERROR_RATE', 'TIME_PRESSURE', 'COMPARISON', 'COMBINED'
                    )),
    severity        VARCHAR(16) NOT NULL DEFAULT 'MEDIUM'
                    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    trigger_data    JSONB,
    is_read         BOOLEAN     NOT NULL DEFAULT FALSE,
    is_resolved     BOOLEAN     NOT NULL DEFAULT FALSE,
    handle_note     TEXT,
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_alert_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_teacher FOREIGN KEY (teacher_id)
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_alert_resolved_by FOREIGN KEY (resolved_by)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_alert_student ON alert_records (student_id);
CREATE INDEX idx_alert_teacher ON alert_records (teacher_id);
CREATE INDEX idx_alert_active ON alert_records (is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX idx_alert_severity ON alert_records (severity);
CREATE INDEX idx_alert_type_severity ON alert_records (alert_type, severity);
CREATE INDEX idx_alert_created_at ON alert_records (created_at DESC);

-- ============================================================================
-- 13. chat_history — 对话历史表
--     存储用户与 AI 助手的对话，支持 LLM+RAG 智能答疑功能
-- ============================================================================
CREATE TABLE chat_history (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL,
    knowledge_point_id  UUID,
    session_id          UUID        NOT NULL,
    role                VARCHAR(16) NOT NULL
                        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    message_type        VARCHAR(32)
                        CHECK (message_type IN (
                            'TEXT', 'QUESTION', 'ANSWER',
                            'HINT', 'FEEDBACK', 'ERROR_ANALYSIS'
                        )),
    content             TEXT        NOT NULL,
    token_count         INTEGER     DEFAULT 0,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ch_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ch_kp FOREIGN KEY (knowledge_point_id)
        REFERENCES knowledge_points(id) ON DELETE SET NULL
);

CREATE INDEX idx_ch_user_session ON chat_history (user_id, session_id);
CREATE INDEX idx_ch_session_created ON chat_history (session_id, created_at);

-- ============================================================================
-- 14. study_sessions — 学习会话表
--     记录每次专注学习时段，与 WebSocket 实时通信联动
-- ============================================================================
CREATE TABLE study_sessions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID        NOT NULL,
    knowledge_point_id      UUID,
    learning_path_node_id   UUID,
    start_time              TIMESTAMPTZ NOT NULL,
    end_time                TIMESTAMPTZ,
    duration_seconds        INTEGER     DEFAULT 0,
    questions_answered      INTEGER     DEFAULT 0,
    correct_count           INTEGER     DEFAULT 0,
    focus_score             DOUBLE PRECISION
                            CHECK (focus_score >= 0 AND focus_score <= 100),
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'COMPLETED', 'INTERRUPTED')),
    interrupt_reason        TEXT,
    metadata                JSONB       DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ss_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ss_node FOREIGN KEY (learning_path_node_id)
        REFERENCES learning_path_nodes(id) ON DELETE SET NULL
);

CREATE INDEX idx_ss_student ON study_sessions (student_id);
CREATE INDEX idx_ss_active ON study_sessions (student_id, status)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_ss_start_time ON study_sessions (student_id, start_time DESC);


-- ============================================================================
-- 审计触发器：自动更新 updated_at 列
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为所有包含 updated_at 列的表创建触发器
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY[
            'users', 'student_profiles', 'courses', 'knowledge_points',
            'learning_paths', 'learning_path_nodes', 'questions',
            'error_records', 'review_records', 'alert_records',
            'study_sessions'
        ])
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON %I
             FOR EACH ROW
             EXECUTE FUNCTION update_updated_at_column()',
            tbl, tbl
        );
    END LOOP;
END;
$$;


-- ============================================================================
-- 表关系概览（用于查阅）
-- ============================================================================
/*
  1. users
       ├── 1:1 → student_profiles (user_id)
       ├── 1:N → courses (created_by)
       ├── 1:N → learning_paths (student_id)
       ├── 1:N → learning_paths (created_by)
       ├── 1:N → questions (created_by)
       ├── 1:N → answer_records (student_id)
       ├── 1:N → error_records (student_id)
       ├── 1:N → review_records (student_id)
       ├── 1:N → alert_records (student_id / teacher_id / resolved_by)
       ├── 1:N → chat_history (user_id)
       └── 1:N → study_sessions (student_id)

  2. courses
       ├── 1:N → knowledge_points (course_id)
       └── 1:N → questions (course_id)

  3. knowledge_points
       ├── 1:N → knowledge_points (parent_kp_id, 自引用树)
       ├── 1:N → knowledge_relations (source/target)
       ├── 1:N → learning_path_nodes (knowledge_point_id)
       ├── 1:N → questions (knowledge_point_id)
       ├── 1:N → answer_records (knowledge_point_id)
       ├── 1:N → error_records (knowledge_point_id)
       ├── 1:N → review_records (knowledge_point_id)
       └── 1:N → chat_history (knowledge_point_id)

  4. learning_paths
       └── 1:N → learning_path_nodes (learning_path_id)

  5. learning_path_nodes
       └── 1:N → study_sessions (learning_path_node_id)

  6. error_records
       └── 1:N → review_records (error_record_id)
*/
-- end of migration script
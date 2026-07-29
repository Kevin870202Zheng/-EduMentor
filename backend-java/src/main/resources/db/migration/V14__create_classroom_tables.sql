-- ============================================================================
-- EduMentor (智学导师) — 沉浸式智慧课堂 数据表
-- Flyway Migration: V14__create_classroom_tables.sql
--
-- 新增 5 张表，支撑 v2.0 课堂功能：
--   classrooms         — 课堂主表（知识点 → AI 生成的课堂教学内容）
--   scenes             — 教学场景（课堂由多个场景组成）
--   scene_actions      — 教学动作（每个场景包含多个教学动作）
--   classroom_progress — 学生学习课堂的进度记录
--   scene_quiz_records — 课堂内 Quiz 作答记录
--
-- 依赖: V1__init_schema.sql (users, courses, knowledge_points)
-- ============================================================================

-- ============================================================================
-- 1. classrooms — 课堂主表
--    一个知识点可以对应多个课堂版本（不同难度/学情适配）
-- ============================================================================
CREATE TABLE classrooms (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id               UUID            NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    knowledge_point_id      UUID            NOT NULL REFERENCES knowledge_points(id) ON DELETE CASCADE,
    title                   VARCHAR(255)    NOT NULL,
    description             TEXT,
    difficulty              INTEGER         NOT NULL DEFAULT 3
                            CHECK (difficulty BETWEEN 1 AND 5),
    total_duration_seconds  INTEGER,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'draft'
                            CHECK (status IN ('draft', 'published', 'archived')),
    scene_count             INTEGER         NOT NULL DEFAULT 0,
    version                 INTEGER         NOT NULL DEFAULT 1,
    metadata                JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_classrooms_course ON classrooms (course_id);
CREATE INDEX idx_classrooms_kp ON classrooms (knowledge_point_id);
CREATE INDEX idx_classrooms_status ON classrooms (status);
CREATE INDEX idx_classrooms_course_kp ON classrooms (course_id, knowledge_point_id);

-- ============================================================================
-- 2. scenes — 教学场景表
--    每个课堂包含 3-8 个教学场景
-- ============================================================================
CREATE TABLE scenes (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id            UUID            NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    title                   VARCHAR(255)    NOT NULL,
    description             TEXT,
    scene_type              VARCHAR(16)     NOT NULL
                            CHECK (scene_type IN ('slide', 'quiz', 'interactive', 'discussion', 'review')),
    order_index             INTEGER         NOT NULL DEFAULT 0,
    estimated_duration_seconds INTEGER,
    content_json            JSONB           NOT NULL DEFAULT '{}',
    metadata                JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_scenes_classroom ON scenes (classroom_id);
CREATE INDEX idx_scenes_classroom_order ON scenes (classroom_id, order_index);

-- ============================================================================
-- 3. scene_actions — 教学动作表
--    每个场景包含多个按顺序执行的教学动作
-- ============================================================================
CREATE TABLE scene_actions (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    scene_id                UUID            NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    action_type             VARCHAR(32)     NOT NULL
                            CHECK (action_type IN (
                                'speech', 'speech_with_highlight',
                                'wb_draw_text', 'wb_draw_diagram',
                                'quiz', 'discussion',
                                'scene_transition', 'pause_for_thought',
                                'code_demo'
                            )),
    order_index             INTEGER         NOT NULL DEFAULT 0,
    params_json             JSONB           NOT NULL DEFAULT '{}',
    duration_ms             INTEGER,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_scene_actions_scene ON scene_actions (scene_id);
CREATE INDEX idx_scene_actions_scene_order ON scene_actions (scene_id, order_index);

-- ============================================================================
-- 4. classroom_progress — 学生学习课堂的进度记录
--    支持断点续播：记录当前播放到的场景和动作位置
-- ============================================================================
CREATE TABLE classroom_progress (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    classroom_id            UUID            NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    status                  VARCHAR(16)     NOT NULL DEFAULT 'not_started'
                            CHECK (status IN ('not_started', 'in_progress', 'completed', 'paused')),
    current_scene_id        UUID            REFERENCES scenes(id) ON DELETE SET NULL,
    current_action_order    INTEGER         NOT NULL DEFAULT 0,
    scenes_completed        INTEGER         NOT NULL DEFAULT 0,
    total_scenes            INTEGER         NOT NULL DEFAULT 0,
    quiz_correct_count      INTEGER         NOT NULL DEFAULT 0,
    quiz_total_count        INTEGER         NOT NULL DEFAULT 0,
    total_watch_seconds     INTEGER         NOT NULL DEFAULT 0,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    last_accessed_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE(student_id, classroom_id)
);

CREATE INDEX idx_cp_student ON classroom_progress (student_id);
CREATE INDEX idx_cp_classroom ON classroom_progress (classroom_id);
CREATE INDEX idx_cp_status ON classroom_progress (status);
CREATE INDEX idx_cp_student_status ON classroom_progress (student_id, status);

-- ============================================================================
-- 5. scene_quiz_records — 课堂内 Quiz 作答记录
--    与 BKT 引擎联动的基础数据
-- ============================================================================
CREATE TABLE scene_quiz_records (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id              UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scene_id                UUID            NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    knowledge_point_id      UUID            REFERENCES knowledge_points(id) ON DELETE SET NULL,
    quiz_data               JSONB           NOT NULL,
    student_answer          JSONB,
    is_correct              BOOLEAN,
    ai_feedback             TEXT,
    attempt_count           INTEGER         NOT NULL DEFAULT 1,
    answered_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_sqr_student ON scene_quiz_records (student_id);
CREATE INDEX idx_sqr_scene ON scene_quiz_records (scene_id);
CREATE INDEX idx_sqr_kp ON scene_quiz_records (knowledge_point_id);
CREATE INDEX idx_sqr_student_scene ON scene_quiz_records (student_id, scene_id);
